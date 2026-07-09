package com.bizsage.api.ops;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * V2: Alert rule evaluation engine.
 *
 * <p>Reads configured thresholds from {@code admin_risk_rules} and the
 * {@code application.yml} alert configuration, evaluates current system
 * state, and creates {@code alert_events} when thresholds are breached.
 *
 * <p>De-duplication: before creating a new alert, the engine checks for
 * an existing OPEN alert for the same component. If found, the alert is
 * not duplicated.
 */
@Service
public class AlertRuleEngine {

  private static final Logger log = LoggerFactory.getLogger(AlertRuleEngine.class);

  private final JdbcTemplate jdbc;
  private final double apiErrorRateThreshold;
  private final double collectorFailureRateThreshold;
  private final int knowledgeStalenessDays;

  public AlertRuleEngine(
      JdbcTemplate jdbc,
      @Value("${bizsage.alerts.api-error-rate-threshold:0.05}") double apiErrorRateThreshold,
      @Value("${bizsage.alerts.collector-failure-rate-threshold:0.20}") double collectorFailureRateThreshold,
      @Value("${bizsage.alerts.knowledge-staleness-days:30}") int knowledgeStalenessDays) {
    this.jdbc = jdbc;
    this.apiErrorRateThreshold = apiErrorRateThreshold;
    this.collectorFailureRateThreshold = collectorFailureRateThreshold;
    this.knowledgeStalenessDays = knowledgeStalenessDays;
  }

  /** Run all alert evaluations. Called by the scheduler every 60 seconds. */
  public void evaluateAll() {
    log.debug("Starting alert rule evaluation cycle");
    evaluateApiErrorRate();
    evaluateCollectorFailureRate();
    evaluateCircuitBreakerState();
    evaluateDbConnectionPool();
    evaluateKnowledgeStaleness();
    log.debug("Alert rule evaluation cycle complete");
  }

  // ── Individual evaluators ────────────────────────────────────

  void evaluateApiErrorRate() {
    // Count error audit logs in the last 5 minutes vs total API calls
    Integer recentErrors = jdbc.queryForObject("""
        select count(*) from audit_logs
         where result = 'ERROR'
           and create_time >= date_sub(current_timestamp, interval 5 minute)
        """, Integer.class);
    Integer recentTotal = jdbc.queryForObject("""
        select count(*) from audit_logs
         where create_time >= date_sub(current_timestamp, interval 5 minute)
        """, Integer.class);

    if (recentErrors == null || recentTotal == null || recentTotal == 0) return;
    double errorRate = (double) recentErrors / recentTotal;

    if (errorRate > apiErrorRateThreshold) {
      createAlert("P1", "api-gateway",
          String.format("API error rate %.1f%% exceeds threshold %.1f%% (last 5 min: %d errors / %d requests)",
              errorRate * 100, apiErrorRateThreshold * 100, recentErrors, recentTotal));
    }
  }

  void evaluateCollectorFailureRate() {
    // Recent collection job runs
    Integer totalRuns = jdbc.queryForObject("""
        select count(*) from admin_collection_job_runs
         where start_time >= date_sub(current_timestamp, interval 15 minute)
        """, Integer.class);
    Integer failedRuns = jdbc.queryForObject("""
        select count(*) from admin_collection_job_runs
         where status = 'FAILED'
           and start_time >= date_sub(current_timestamp, interval 15 minute)
        """, Integer.class);

    if (totalRuns == null || failedRuns == null || totalRuns == 0) return;
    double failureRate = (double) failedRuns / totalRuns;

    if (failureRate > collectorFailureRateThreshold) {
      createAlert("P1", "collector-pipeline",
          String.format("Collector failure rate %.1f%% exceeds threshold %.1f%% (last 15 min: %d failed / %d runs)",
              failureRate * 100, collectorFailureRateThreshold * 100, failedRuns, totalRuns));
    }
  }

  void evaluateCircuitBreakerState() {
    List<Map<String, Object>> openCircuits = jdbc.queryForList("""
        select source_config_id, name from collection_source_configs
         where circuit_state = 'OPEN'
        """);
    for (Map<String, Object> row : openCircuits) {
      Object configId = row.get("source_config_id");
      Object name = row.get("name");
      createAlert("P0", "circuit-breaker",
          String.format("Circuit breaker OPEN for collection source #%s (%s)",
              configId, name));
    }
  }

  void evaluateDbConnectionPool() {
    // Simple check: can we query the database?
    try {
      Integer active = jdbc.queryForObject(
          "select count(*) from information_schema.processlist where db = database()",
          Integer.class);
      if (active != null && active > 40) {
        createAlert("P1", "database",
            String.format("Database connection pool high: %d active connections", active));
      }
    } catch (Exception e) {
      createAlert("P0", "database",
          "Database connection check failed: " + e.getMessage());
    }
  }

  void evaluateKnowledgeStaleness() {
    Integer staleCount = jdbc.queryForObject("""
        select count(*) from knowledge_items
         where update_time < date_sub(current_timestamp, interval ? day)
        """, Integer.class, knowledgeStalenessDays);

    if (staleCount != null && staleCount > 0) {
      createAlert("P2", "knowledge-base",
          String.format("%d knowledge items have not been updated in %d+ days",
              staleCount, knowledgeStalenessDays));
    }
  }

  // ── Alert creation with de-duplication ───────────────────────

  private void createAlert(String level, String component, String message) {
    // De-duplicate: skip if an OPEN alert for this component already exists
    Integer existing = jdbc.queryForObject("""
        select count(*) from alert_events
         where component = ? and status not in ('CLOSED')
         limit 1
        """, Integer.class, component);
    if (existing != null && existing > 0) {
      log.debug("Alert suppressed (existing OPEN for {}): {}", component, message);
      return;
    }

    jdbc.update("""
        insert into alert_events (alert_level, component, message, status, region_id, industry_id)
        values (?, ?, ?, 'OPEN', 'global', 'global')
        """, level, component, message);
    log.info("Alert created: [{}] {} — {}", level, component, message);
  }
}
