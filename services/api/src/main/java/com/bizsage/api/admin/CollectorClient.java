package com.bizsage.api.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CollectorClient {
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
  };
  private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {
  };
  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
  private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script.*?</script>|<style.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String collectorUrl;

  public CollectorClient(@Value("${COLLECTOR_URL:http://localhost:8200}") String collectorUrl, ObjectMapper objectMapper) {
    this.collectorUrl = collectorUrl;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().baseUrl(collectorUrl).build();
  }

  /**
   * 采集并治理外部数据。
   *
   * <p>使用 crawlerPages 缓存避免短时间内重复抓取相同 URL；缓存 TTL 由缓存配置控制。
   */
  @Cacheable(value = "crawlerPages", key = "#sourceType + ':' + T(java.util.Objects).hash(#payload)")
  public List<Map<String, Object>> collectAndGovern(String sourceType, Map<String, Object> payload) {
    List<Map<String, Object>> collected = collect(sourceType, payload);
    return govern(collected);
  }

  private List<Map<String, Object>> collect(String sourceType, Map<String, Object> payload) {
    if (collectorUrl.startsWith("embedded://")) {
      // embedded 模式用于本地测试或 Collector 服务不可用的轻量环境。
      return embeddedCollect(sourceType, payload);
    }
    try {
      Map<String, Object> envelope = restClient.post()
          .uri(endpointForType(sourceType))
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            throw new IllegalArgumentException("collector request failed (HTTP " + res.getStatusCode().value() + "): " + new String(body));
          })
          .body(MAP_TYPE);
      return readRecords(envelope);
    } catch (ResourceAccessException ex) {
      // 将网络异常转换为管理端可读错误，避免暴露底层 RestClient 细节。
      Throwable root = ex.getCause();
      if (root instanceof ConnectException) {
        throw new IllegalArgumentException("collector is not reachable");
      }
      if (root instanceof TimeoutException) {
        throw new IllegalArgumentException("collector request timed out");
      }
      throw new IllegalArgumentException("collector connection failed: " + ex.getMessage());
    } catch (Exception ex) {
      throw ex instanceof IllegalArgumentException ? (IllegalArgumentException) ex : new IllegalArgumentException("collector request failed: " + ex.getMessage());
    }
  }

  private List<Map<String, Object>> govern(List<Map<String, Object>> records) {
    if (collectorUrl.startsWith("embedded://")) {
      // embedded 模式下采集函数已返回规范化记录，这里直接透传。
      return embeddedGovern(records);
    }
    try {
      Map<String, Object> envelope = restClient.post()
          .uri("/govern")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("records", records))
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            throw new IllegalArgumentException("collector governance failed (HTTP " + res.getStatusCode().value() + "): " + new String(body));
          })
          .body(MAP_TYPE);
      return readRecords(envelope);
    } catch (Exception ex) {
      throw ex instanceof IllegalArgumentException ? (IllegalArgumentException) ex : new IllegalArgumentException("collector governance failed: " + ex.getMessage());
    }
  }

  private List<Map<String, Object>> embeddedCollect(String sourceType, Map<String, Object> payload) {
    // 内嵌采集只覆盖当前系统使用的三类来源，行为需与 Python Collector 的输出契约一致。
    String normalizedType = normalizeType(sourceType);
    if ("PUBLIC_PAGE".equals(normalizedType)) {
      String url = stringValue(payload.get("url"), stringValue(payload.get("sourceUrl"), "embedded://page"));
      String html = stringValue(payload.get("html"), "");
      return List.of(normalizedRecord(
          "PUBLIC_PAGE",
          stringValue(payload.get("source_id"), stringValue(payload.get("sourceId"), "public-page")),
          titleFromHtml(html, url),
          bodyFromHtml(html),
          stringValue(payload.get("industry_id"), stringValue(payload.get("industryId"), "general")),
          stringValue(payload.get("region_id"), stringValue(payload.get("regionId"), "cn-default")),
          stringValue(payload.get("link_id"), stringValue(payload.get("linkId"), "public-page")),
          0.6,
          0.6,
          url));
    }
    if ("MOCK_API".equals(normalizedType)) {
      List<Map<String, Object>> items = objectMapper.convertValue(payload.getOrDefault("items", List.of()), LIST_OF_MAP_TYPE);
      List<Map<String, Object>> records = new ArrayList<>();
      for (Map<String, Object> item : items) {
        records.add(normalizedRecord(
            "THIRD_PARTY_API_MOCK",
            "mock-api",
            stringValue(item.get("title"), "Untitled item"),
            stringValue(item.get("content"), ""),
            stringValue(item.get("industry_id"), stringValue(item.get("industryId"), "general")),
            stringValue(item.get("region_id"), stringValue(item.get("regionId"), "cn-default")),
            stringValue(item.get("link_id"), stringValue(item.get("linkId"), "third-party")),
            doubleValue(item.get("confidence"), 0.85),
            doubleValue(item.get("weight"), 0.85),
            stringValue(item.get("url"), null)));
      }
      return records;
    }
    if ("FORM".equals(normalizedType)) {
      return List.of(normalizedRecord(
          "USER_PRIVATE_FORM",
          stringValue(payload.get("source_id"), stringValue(payload.get("sourceId"), "user-private")),
          stringValue(payload.get("title"), "Untitled form"),
          stringValue(payload.get("content"), ""),
          stringValue(payload.get("industry_id"), stringValue(payload.get("industryId"), "general")),
          stringValue(payload.get("region_id"), stringValue(payload.get("regionId"), "cn-default")),
          stringValue(payload.get("link_id"), stringValue(payload.get("linkId"), "business-data")),
          1.0,
          1.0,
          null));
    }
    throw new IllegalArgumentException("unsupported source type");
  }

  private List<Map<String, Object>> embeddedGovern(List<Map<String, Object>> records) {
    // 内嵌治理不做 SimHash/传闻过滤，仅作为开发环境兜底。
    return records;
  }

  private Map<String, Object> normalizedRecord(
      String sourceType,
      String sourceId,
      String title,
      String content,
      String industryId,
      String regionId,
      String linkId,
      double confidence,
      double weight,
      String url) {
    // Collector 与 Admin 入库之间的统一记录格式。
    Map<String, Object> record = new HashMap<>();
    record.put("source_type", sourceType);
    record.put("source_id", sourceId);
    record.put("title", compact(title));
    record.put("content", compact(content));
    record.put("industry_id", industryId);
    record.put("region_id", regionId);
    record.put("link_id", linkId);
    record.put("confidence", confidence);
    record.put("weight", weight);
    record.put("url", url);
    return record;
  }

  private String endpointForType(String sourceType) {
    // 业务来源类型到 Collector HTTP 端点的映射。
    return switch (normalizeType(sourceType)) {
      case "PUBLIC_PAGE" -> "/collect/public-page";
      case "MOCK_API" -> "/collect/mock-api";
      case "FORM" -> "/collect/form";
      default -> throw new IllegalArgumentException("unsupported source type");
    };
  }

  private String normalizeType(String sourceType) {
    // 兼容下划线、短横线和历史别名。
    String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "PUBLIC_PAGE", "PUBLIC-PAGE" -> "PUBLIC_PAGE";
      case "MOCK_API", "MOCK-API" -> "MOCK_API";
      case "FORM", "BUSINESS_FORM", "FORM_BUSINESS_DATA" -> "FORM";
      default -> normalized;
    };
  }

  private List<Map<String, Object>> readRecords(Map<String, Object> envelope) {
    // Collector 响应统一使用 {"records": [...]} 外壳。
    if (envelope == null) {
      return List.of();
    }
    Object raw = envelope.get("records");
    return objectMapper.convertValue(raw == null ? List.of() : raw, LIST_OF_MAP_TYPE);
  }

  private String titleFromHtml(String html, String fallback) {
    // 内嵌模式的轻量标题提取，复杂 HTML 仍交给独立 Collector。
    int open = html == null ? -1 : html.toLowerCase(Locale.ROOT).indexOf("<title>");
    int close = html == null ? -1 : html.toLowerCase(Locale.ROOT).indexOf("</title>");
    if (open >= 0 && close > open) {
      return compact(html.substring(open + 7, close));
    }
    return fallback;
  }

  private String bodyFromHtml(String html) {
    // 移除 script/style 和标签后压缩空白，得到近似正文。
    if (html == null) {
      return "";
    }
    String withoutScripts = SCRIPT_PATTERN.matcher(html).replaceAll(" ");
    return compact(TAG_PATTERN.matcher(withoutScripts).replaceAll(" "));
  }

  private String compact(String text) {
    // 压缩多余空白，保持与 Python Collector normalize_record 接近。
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  private String stringValue(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value);
  }

  private double doubleValue(Object value, double fallback) {
    // 外部 payload 可能传入字符串或数字，解析失败时使用默认权重/置信度。
    if (value == null) {
      return fallback;
    }
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }
}
