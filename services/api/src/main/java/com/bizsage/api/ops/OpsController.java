package com.bizsage.api.ops;

import com.bizsage.api.cache.CacheMetrics;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class OpsController {
  private final OpsReadService opsReadService;
  private final CacheMetrics cacheMetrics;
  private final SlaService slaService;
  private final String grayCohort;

  public OpsController(
      OpsReadService opsReadService,
      CacheMetrics cacheMetrics,
      SlaService slaService,
      @Value("${bizsage.gray-release.cohort:internal-operators-and-seed-paid-users}") String grayCohort) {
    this.opsReadService = opsReadService;
    this.cacheMetrics = cacheMetrics;
    this.slaService = slaService;
    this.grayCohort = grayCohort;
  }

  @GetMapping("/metrics")
  ApiResponse<Map<String, Object>> metrics(HttpServletRequest request) {
    return ApiResponse.ok(Map.of(
        "grayCohort", grayCohort,
        "cacheHitRateTarget", 0.7,
        "databaseRecoveryDataLossHoursTarget", 6,
        "environment", "prod-gray"), requestId(request));
  }

  /** V2: Returns per-cache hit/miss/rate statistics for monitoring dashboards. */
  @GetMapping("/cache-stats")
  ApiResponse<Map<String, Object>> cacheStats(HttpServletRequest request) {
    Map<String, Object> allMetrics = cacheMetrics.allMetrics();
    double aggregateHitRate = computeAggregateHitRate(allMetrics);
    return ApiResponse.ok(Map.of(
        "caches", allMetrics,
        "aggregateHitRate", String.format("%.2f", aggregateHitRate),
        "targetHitRate", 0.7,
        "meetsTarget", aggregateHitRate >= 0.7), requestId(request));
  }

  /** V2: Returns SLA statistics (uptime, error rate, latency percentiles). */
  @GetMapping("/sla")
  ApiResponse<Map<String, Object>> sla(
      @RequestParam(defaultValue = "24h") String window,
      HttpServletRequest request) {
    return ApiResponse.ok(slaService.computeSla(window), requestId(request));
  }

  private double computeAggregateHitRate(Map<String, Object> allMetrics) {
    long totalHits = 0, totalMisses = 0;
    for (Object val : allMetrics.values()) {
      if (val instanceof Map<?, ?> m) {
        Object hits = m.get("hits");
        Object misses = m.get("misses");
        if (hits instanceof Number h) totalHits += h.longValue();
        if (misses instanceof Number mis) totalMisses += mis.longValue();
      }
    }
    long total = totalHits + totalMisses;
    return total > 0 ? (double) totalHits / total : -1.0;
  }

  @GetMapping("/alerts")
  ApiResponse<List<Map<String, Object>>> alerts(HttpServletRequest request) {
    return ApiResponse.ok(opsReadService.listAlerts(), requestId(request));
  }

  @GetMapping("/review-work-orders")
  ApiResponse<List<Map<String, Object>>> reviewWorkOrders(HttpServletRequest request) {
    return ApiResponse.ok(opsReadService.listReviewWorkOrders(), requestId(request));
  }

  @GetMapping("/audit-logs")
  ApiResponse<List<Map<String, Object>>> auditLogs(HttpServletRequest request) {
    return ApiResponse.ok(opsReadService.listAuditLogs(), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
