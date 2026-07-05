package com.bizsage.api.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
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
}
