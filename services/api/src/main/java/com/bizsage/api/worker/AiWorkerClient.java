package com.bizsage.api.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * AI Worker 微服务 HTTP 客户端。
 *
 * <p>负责诊断、学习、模式切换、知识同步和记忆向量同步。所有推理类失败都会抛出
 * {@link AiWorkerException}，调用方不得回退到本地模板答案，避免业务诊断出现静默降级。
 */
@Component
public class AiWorkerClient {

  private static final Logger log = LoggerFactory.getLogger(AiWorkerClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public AiWorkerClient(
      @Value("${AI_WORKER_URL:http://localhost:8100}") String workerUrl,
      ObjectMapper objectMapper) {
    this.restClient = RestClient.builder()
        .baseUrl(workerUrl)
        .build();
    this.objectMapper = objectMapper;
  }

  /**
   * 向 AI Worker 发送诊断请求，并返回结构化结果。
   *
   * @throws AiWorkerException 当 Worker 不可达、超时、返回非 2xx 或响应体无效时抛出
   */
  public DiagnoseResponse diagnose(DiagnoseRequest request) {
    try {
      DiagnoseResponse response = restClient.post()
          .uri("/agent/diagnose")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            String bodyText = new String(body);
            log.error("AI worker returned 5xx: status={}, body={}", res.getStatusCode().value(), bodyText);
            throw new AiWorkerException(
                "AI worker service error (HTTP " + res.getStatusCode().value() + "): " + bodyText,
                res.getStatusCode().value());
          })
          .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            String bodyText = new String(body);
            log.error("AI worker returned 4xx: status={}, body={}", res.getStatusCode().value(), bodyText);
            throw new AiWorkerException(
                "AI worker request error (HTTP " + res.getStatusCode().value() + "): " + bodyText,
                res.getStatusCode().value());
          })
          .body(DiagnoseResponse.class);

      if (response == null) {
        // 空响应无法判断自检状态，必须视为 Worker 失败。
        throw new AiWorkerException("AI worker returned null response");
      }

      log.info("AI worker diagnosis complete: selfCheckStatus={}, sources={}",
          response.selfCheckStatus(),
          response.sources() != null ? response.sources().size() : 0);

      return response;

    } catch (AiWorkerException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      // 将底层连接异常归一化为业务可识别的 Worker 错误，便于 SSE 端映射错误码。
      Throwable root = ex.getCause();
      if (root instanceof ConnectException) {
        throw new AiWorkerException(
            "AI worker is not reachable — please check that the worker service is running", ex);
      }
      if (root instanceof TimeoutException) {
        throw new AiWorkerException(
            "AI worker request timed out", ex);
      }
      throw new AiWorkerException(
          "AI worker connection failed: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new AiWorkerException(
          "Unexpected error calling AI worker: " + ex.getMessage(), ex);
    }
  }

  public DiagnoseResponse streamDiagnose(
      DiagnoseRequest request, AgentStreamListener listener) {
    return streamAgent("/agent/diagnose/stream", request, listener);
  }

  /**
   * 通过 /health 检查 AI Worker 是否可达。
   *
   * @return Worker 返回健康状态时为 true
   */
  public boolean isHealthy() {
    try {
      String status = restClient.get()
          .uri("/health")
          .retrieve()
          .body(String.class);
      return status != null && status.contains("UP");
    } catch (Exception ex) {
      log.warn("AI worker health check failed: {}", ex.getMessage());
      return false;
    }
  }

  // 知识同步。

  /**
   * 将知识增量 upsert 到 AI Worker 的持久 Qdrant 集合。
   *
   * <p>用于知识发布或更新后的增量同步；同一业务 ID 会覆盖旧向量，保持幂等。
   *
   * @return Worker 实际同步的条数
   */
  public int syncKnowledge(List<Map<String, Object>> items) {
    if (items == null || items.isEmpty()) {
      return 0;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("knowledge", items);
    body.put("clear_before", false);

    try {
      Map<String, Object> response = restClient.post()
          .uri("/knowledge/sync")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      if (response != null && response.get("synced") instanceof Number synced) {
        log.info("Synced {} knowledge items to AI worker Qdrant", synced.intValue());
        return synced.intValue();
      }
      return 0;
    } catch (Exception ex) {
      // 知识同步失败不应影响 API 主流程，后续启动全量同步可修复索引缺口。
      log.warn("Knowledge sync to AI worker failed (non-fatal): {}", ex.getMessage());
      return 0;
    }
  }

  /**
   * 清空并全量重建 AI Worker 持久 Qdrant 集合。
   *
   * <p>通常在 API 启动时执行，保证向量集合与数据库权威知识一致。
   */
  public int syncAllKnowledge(List<Map<String, Object>> items) {
    if (items == null || items.isEmpty()) {
      return 0;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("knowledge", items);
    body.put("clear_before", true);

    try {
      Map<String, Object> response = restClient.post()
          .uri("/knowledge/sync")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      if (response != null && response.get("synced") instanceof Number synced) {
        log.info("Full-resync: loaded {} knowledge items into AI worker Qdrant", synced.intValue());
        return synced.intValue();
      }
      return 0;
    } catch (Exception ex) {
      log.warn("Full knowledge sync to AI worker failed (non-fatal): {}", ex.getMessage());
      return 0;
    }
  }

  // 记忆向量同步。

  /**
   * 通过 AI Worker 将待同步用户记忆写入 Qdrant。
   *
   * <p>AI Worker 负责计算向量并写入 bizsage_memory 集合；API 层根据返回的
   * embedding_id 与 qdrant_point_id 更新同步状态。
   *
   * @return 每条成功同步记忆的结果列表
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> syncMemoryEmbeddings(List<Map<String, Object>> memories) {
    if (memories == null || memories.isEmpty()) {
      return List.of();
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("memories", memories);

    try {
      Map<String, Object> response = restClient.post()
          .uri("/memory/sync")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(Map.class);
      if (response != null && response.get("results") instanceof List<?> results) {
        log.info("Synced {} memory embeddings to AI worker Qdrant", results.size());
        return (List<Map<String, Object>>) results;
      }
      return List.of();
    } catch (Exception ex) {
      log.warn("Memory embedding sync to AI worker failed (non-fatal): {}", ex.getMessage());
      return List.of();
    }
  }

  // 学习 Agent。

  /**
   * 向 AI Worker 发送学习请求，并返回结构化学习结果。
   *
   * @throws AiWorkerException Worker 调用失败时抛出
   */
  public DiagnoseResponse learn(LearningRequest request) {
    try {
      DiagnoseResponse response = restClient.post()
          .uri("/agent/learn")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            throw new AiWorkerException("AI worker learning error: " + new String(body),
                res.getStatusCode().value());
          })
          .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            throw new AiWorkerException("AI worker learning request error: " + new String(body),
                res.getStatusCode().value());
          })
          .body(DiagnoseResponse.class);

      if (response == null) {
        // 学习结果也必须保留自检和来源字段，空响应不能被当作成功。
        throw new AiWorkerException("AI worker returned null learning response");
      }
      log.info("AI worker learning complete: mode={}, chainNodeId={}, sources={}",
          response.mode(), response.chainNodeId(),
          response.sources() != null ? response.sources().size() : 0);
      return response;
    } catch (AiWorkerException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      Throwable root = ex.getCause();
      if (root instanceof ConnectException) {
        throw new AiWorkerException("AI worker is not reachable", ex);
      }
      if (root instanceof TimeoutException) {
        throw new AiWorkerException("AI worker learning request timed out", ex);
      }
      throw new AiWorkerException("AI worker connection failed: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new AiWorkerException("Unexpected error calling AI worker learn: " + ex.getMessage(), ex);
    }
  }

  public DiagnoseResponse streamLearn(
      LearningRequest request, AgentStreamListener listener) {
    return streamAgent("/agent/learn/stream", request, listener);
  }

  /**
   * 执行学习与诊断之间的双 Agent 模式切换。
   */
  public DiagnoseResponse transition(TransitionRequest request) {
    try {
      DiagnoseResponse response = restClient.post()
          .uri("/agent/transition")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            byte[] body = res.getBody().readAllBytes();
            throw new AiWorkerException("AI worker transition error: " + new String(body),
                res.getStatusCode().value());
          })
          .body(DiagnoseResponse.class);

      if (response == null) {
        throw new AiWorkerException("AI worker returned null transition response");
      }
      log.info("AI worker transition complete: {} → {}, mode={}",
          request.fromMode(), request.toMode(), response.mode());
      return response;
    } catch (AiWorkerException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      Throwable root = ex.getCause();
      if (root instanceof ConnectException) {
        throw new AiWorkerException("AI worker is not reachable", ex);
      }
      if (root instanceof TimeoutException) {
        throw new AiWorkerException("AI worker transition request timed out", ex);
      }
      throw new AiWorkerException("AI worker connection failed: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new AiWorkerException("Unexpected error calling AI worker transition: " + ex.getMessage(), ex);
    }
  }

  public DiagnoseResponse streamTransition(
      TransitionRequest request, AgentStreamListener listener) {
    return streamAgent("/agent/transition/stream", request, listener);
  }

  private DiagnoseResponse streamAgent(
      String path, Object requestBody, AgentStreamListener listener) {
    try {
      DiagnoseResponse result = restClient.post()
          .uri(path)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.TEXT_EVENT_STREAM)
          .body(requestBody)
          .exchange((request, response) -> {
            if (response.getStatusCode().isError()) {
              String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
              throw new AiWorkerException(
                  "AI worker stream error (HTTP " + response.getStatusCode().value() + "): " + body,
                  response.getStatusCode().value());
            }
            return readAgentStream(response.getBody(), listener);
          });
      if (result == null) {
        throw new AiWorkerException("AI worker stream ended without a result event");
      }
      return result;
    } catch (AiWorkerException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
      Throwable root = ex.getCause();
      if (root instanceof ConnectException) {
        throw new AiWorkerException("AI worker is not reachable", ex);
      }
      if (root instanceof TimeoutException) {
        throw new AiWorkerException("AI worker stream timed out", ex);
      }
      throw new AiWorkerException("AI worker connection failed: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new AiWorkerException("Unexpected error reading AI worker stream: " + ex.getMessage(), ex);
    }
  }

  private DiagnoseResponse readAgentStream(
      java.io.InputStream inputStream, AgentStreamListener listener) throws IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String eventName = "message";
      StringBuilder data = new StringBuilder();
      DiagnoseResponse result = null;
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          if (!data.isEmpty()) {
            result = dispatchWorkerEvent(eventName, data.toString(), listener, result);
          }
          eventName = "message";
          data.setLength(0);
          continue;
        }
        if (line.startsWith("event:")) {
          eventName = line.substring("event:".length()).trim();
        } else if (line.startsWith("data:")) {
          if (!data.isEmpty()) data.append('\n');
          data.append(line.substring("data:".length()).stripLeading());
        }
      }
      if (!data.isEmpty()) {
        result = dispatchWorkerEvent(eventName, data.toString(), listener, result);
      }
      return result;
    }
  }

  private DiagnoseResponse dispatchWorkerEvent(
      String eventName,
      String payload,
      AgentStreamListener listener,
      DiagnoseResponse currentResult) throws IOException {
    JsonNode data = objectMapper.readTree(payload);
    if ("result".equals(eventName)) {
      return objectMapper.treeToValue(data, DiagnoseResponse.class);
    }
    if ("error".equals(eventName)) {
      String code = data.path("error").asText("WORKER_ERROR");
      String message = data.path("message").asText("AI worker stream failed");
      throw new AiWorkerException(code + ": " + message);
    }
    if ("status".equals(eventName) || "delta".equals(eventName) || "reset".equals(eventName)) {
      listener.onEvent(new AgentStreamEvent(eventName, data));
    }
    return currentResult;
  }

  /**
   * 按知识 ID 从 AI Worker 持久 Qdrant 集合中删除向量。
   *
   * <p>用于知识下架或删除后的索引清理；失败只记录日志，不阻断主事务。
   */
  public int deleteKnowledge(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    try {
      Map<String, Object> response = restClient.post()
          .uri("/knowledge/delete")
          .contentType(MediaType.APPLICATION_JSON)
          .body(ids)
          .retrieve()
          .body(Map.class);
      if (response != null && response.get("deleted") instanceof Number deleted) {
        log.info("Deleted {} knowledge items from AI worker Qdrant", deleted.intValue());
        return deleted.intValue();
      }
      return 0;
    } catch (Exception ex) {
      log.warn("Knowledge delete from AI worker failed (non-fatal): {}", ex.getMessage());
      return 0;
    }
  }
}
