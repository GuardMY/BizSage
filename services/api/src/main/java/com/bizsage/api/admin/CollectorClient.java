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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CollectorClient {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
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

  /** V2: Collect and govern with crawlerPages cache (15-min TTL) to avoid redundant
   *  re-crawling of the same URLs within a short window. */
  @Cacheable(value = "crawlerPages", key = "#sourceType + ':' + T(java.util.Objects).hash(#payload)")
  public List<Map<String, Object>> collectAndGovern(String sourceType, Map<String, Object> payload) {
    List<Map<String, Object>> collected = collect(sourceType, payload);
    return govern(collected);
  }

  private List<Map<String, Object>> collect(String sourceType, Map<String, Object> payload) {
    if (collectorUrl.startsWith("embedded://")) {
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
    return switch (normalizeType(sourceType)) {
      case "PUBLIC_PAGE" -> "/collect/public-page";
      case "MOCK_API" -> "/collect/mock-api";
      case "FORM" -> "/collect/form";
      default -> throw new IllegalArgumentException("unsupported source type");
    };
  }

  private String normalizeType(String sourceType) {
    String normalized = sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "PUBLIC_PAGE", "PUBLIC-PAGE" -> "PUBLIC_PAGE";
      case "MOCK_API", "MOCK-API" -> "MOCK_API";
      case "FORM", "BUSINESS_FORM", "FORM_BUSINESS_DATA" -> "FORM";
      default -> normalized;
    };
  }

  private List<Map<String, Object>> readRecords(Map<String, Object> envelope) {
    if (envelope == null) {
      return List.of();
    }
    Object raw = envelope.get("records");
    return objectMapper.convertValue(raw == null ? List.of() : raw, LIST_OF_MAP_TYPE);
  }

  private String titleFromHtml(String html, String fallback) {
    int open = html == null ? -1 : html.toLowerCase(Locale.ROOT).indexOf("<title>");
    int close = html == null ? -1 : html.toLowerCase(Locale.ROOT).indexOf("</title>");
    if (open >= 0 && close > open) {
      return compact(html.substring(open + 7, close));
    }
    return fallback;
  }

  private String bodyFromHtml(String html) {
    if (html == null) {
      return "";
    }
    String withoutScripts = SCRIPT_PATTERN.matcher(html).replaceAll(" ");
    return compact(TAG_PATTERN.matcher(withoutScripts).replaceAll(" "));
  }

  private String compact(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  private String stringValue(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value);
  }

  private double doubleValue(Object value, double fallback) {
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
