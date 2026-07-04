package com.bizsage.api.ops;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class OpsController {
  private final JdbcTemplate jdbcTemplate;
  private final String grayCohort;

  public OpsController(
      JdbcTemplate jdbcTemplate,
      @Value("${bizsage.gray-release.cohort:internal-operators-and-seed-paid-users}") String grayCohort) {
    this.jdbcTemplate = jdbcTemplate;
    this.grayCohort = grayCohort;
  }

  @GetMapping("/metrics")
  ApiResponse<Map<String, Object>> metrics(HttpServletRequest request) {
    return ApiResponse.ok(Map.of(
        "grayCohort", grayCohort,
        "cacheHitRateTarget", 0.7,
        "crawlerRtoMinutesTarget", 10,
        "databaseRecoveryDataLossHoursTarget", 6,
        "environment", "prod-gray"), requestId(request));
  }

  @GetMapping("/alerts")
  ApiResponse<List<Map<String, Object>>> alerts(HttpServletRequest request) {
    return ApiResponse.ok(jdbcTemplate.queryForList("""
        select alert_level as level, component as scope, status, owner, message
          from alert_events
         order by id desc
        """), requestId(request));
  }

  @GetMapping("/review-work-orders")
  ApiResponse<List<Map<String, Object>>> reviewWorkOrders(HttpServletRequest request) {
    return ApiResponse.ok(jdbcTemplate.queryForList("""
        select id, reason, status, source_id as sourceId, target_type as targetType, target_id as targetId
          from review_work_orders
         order by id desc
        """), requestId(request));
  }

  @GetMapping("/audit-logs")
  ApiResponse<List<Map<String, Object>>> auditLogs(HttpServletRequest request) {
    return ApiResponse.ok(jdbcTemplate.queryForList("""
        select id, action, actor, result, target_type as targetType, target_id as targetId
          from audit_logs
         order by id desc
        """), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
