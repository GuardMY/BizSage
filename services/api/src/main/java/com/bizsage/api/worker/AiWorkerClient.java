package com.bizsage.api.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
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
 * HTTP client for the AI worker microservice.
 *
 * <p>Calls POST /agent/diagnose and maps the response into a
 * {@link DiagnoseResponse}.  Failures are surfaced as
 * {@link AiWorkerException} — callers must not fall back to local answers.
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
   * Send a diagnosis request to the AI worker and return the structured response.
   *
   * @param request the assembled diagnosis request
   * @return the worker's structured diagnosis response
   * @throws AiWorkerException if the worker is unreachable, times out,
   *         returns a non-200 status, or returns an invalid body
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
        throw new AiWorkerException("AI worker returned null response");
      }

      log.info("AI worker diagnosis complete: selfCheckStatus={}, sources={}",
          response.selfCheckStatus(),
          response.sources() != null ? response.sources().size() : 0);

      return response;

    } catch (AiWorkerException ex) {
      throw ex;
    } catch (ResourceAccessException ex) {
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

  /**
   * Check whether the AI worker is reachable via its /health endpoint.
   *
   * @return true if the worker responds with a healthy status
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

  // ── Knowledge sync ──────────────────────────────────────────────

  /**
   * Upsert knowledge items into the AI worker's persistent Qdrant collection.
   * Used for incremental sync when knowledge is published or updated.
   *
   * @param items knowledge items to upsert (idempotent — same ID overwrites)
   * @return number of synced items
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
      log.warn("Knowledge sync to AI worker failed (non-fatal): {}", ex.getMessage());
      return 0;
    }
  }

  /**
   * Clear and fully reload the persistent Qdrant collection.
   * Used on application startup to ensure Qdrant matches the database.
   *
   * @param items all knowledge items to load
   * @return number of synced items
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

  // ── Memory Embedding Sync ───────────────────────────────────────

  /**
   * Sync pending user memory embeddings to Qdrant via the AI worker.
   *
   * <p>The AI worker computes embeddings and upserts to the bizsage_memory
   * Qdrant collection. Returns a list of {embeddingId, qdrantPointId}
   * maps for the API layer to mark as SYNCED.
   *
   * @param memories list of memory embedding records with id and memory_text
   * @return list of sync results with embedding_id and qdrant_point_id
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

  // ── Learning Agent ──────────────────────────────────────────────

  /**
   * Send a learning request to the AI worker and return the structured response.
   *
   * @param request the assembled learning request
   * @return the worker's structured diagnosis/learning response
   * @throws AiWorkerException on failure
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

  /**
   * Execute a dual-Agent mode transition (LEARNING ↔ DIAGNOSIS).
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

  /**
   * Delete knowledge items from the persistent Qdrant collection by ID.
   * Used when knowledge is unpublished or removed.
   *
   * @param ids knowledge item IDs to remove
   * @return number of deleted items
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
