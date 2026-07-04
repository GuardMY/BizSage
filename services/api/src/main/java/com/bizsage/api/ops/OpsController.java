package com.bizsage.api.ops;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class OpsController {
  @GetMapping("/metrics")
  ApiResponse<Map<String, Object>> metrics(HttpServletRequest request) {
    return ApiResponse.ok(Map.of(
        "grayCohort", "internal-operators-and-seed-paid-users",
        "cacheHitRateTarget", 0.7,
        "crawlerRtoMinutesTarget", 10,
        "databaseRecoveryDataLossHoursTarget", 6,
        "environment", "prod-gray"), requestId(request));
  }

  @GetMapping("/alerts")
  ApiResponse<List<Map<String, Object>>> alerts(HttpServletRequest request) {
    return ApiResponse.ok(List.of(Map.of(
        "level", "P2",
        "scope", "collector",
        "status", "OPEN",
        "owner", "operator")), requestId(request));
  }

  @GetMapping("/review-work-orders")
  ApiResponse<List<Map<String, Object>>> reviewWorkOrders(HttpServletRequest request) {
    return ApiResponse.ok(List.of(Map.of(
        "id", "review-v2-001",
        "reason", "SUSPICIOUS_CONFLICT",
        "status", "PENDING_REVIEW",
        "sourceId", "paid-seed")), requestId(request));
  }

  @GetMapping("/audit-logs")
  ApiResponse<List<Map<String, Object>>> auditLogs(HttpServletRequest request) {
    return ApiResponse.ok(List.of(Map.of(
        "id", "audit-v2-m0",
        "action", "V2_M0_SCOPE_LOCK",
        "actor", "operator",
        "result", "APPROVED")), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
