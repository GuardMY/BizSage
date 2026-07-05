package com.bizsage.api.health;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.worker.AiWorkerClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

  private final AiWorkerClient aiWorkerClient;

  public HealthController(AiWorkerClient aiWorkerClient) {
    this.aiWorkerClient = aiWorkerClient;
  }

  @GetMapping
  ApiResponse<Map<String, String>> health(HttpServletRequest request) {
    boolean workerUp = aiWorkerClient.isHealthy();
    Map<String, String> status = new LinkedHashMap<>();
    status.put("status", "UP");
    status.put("worker", workerUp ? "UP" : "DOWN");
    return ApiResponse.ok(status, request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
