package com.bizsage.api.ops;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlertRuleEngine {
  private static final Logger log = LoggerFactory.getLogger(AlertRuleEngine.class);

  private final OpsQueryMapper opsQueryMapper;
  private final double apiErrorRateThreshold;
  private final int knowledgeStalenessDays;

  public AlertRuleEngine(
      OpsQueryMapper opsQueryMapper,
      @Value("${bizsage.alerts.api-error-rate-threshold:0.05}") double apiErrorRateThreshold,
      @Value("${bizsage.alerts.knowledge-staleness-days:30}") int knowledgeStalenessDays) {
    this.opsQueryMapper = opsQueryMapper;
    this.apiErrorRateThreshold = apiErrorRateThreshold;
    this.knowledgeStalenessDays = knowledgeStalenessDays;
  }

  public void evaluateAll() {
    log.debug("Starting alert rule evaluation cycle");
    evaluateApiErrorRate();
    evaluateDbConnectionPool();
    evaluateKnowledgeStaleness();
    log.debug("Alert rule evaluation cycle complete");
  }

  void evaluateApiErrorRate() {
    Integer recentErrors = opsQueryMapper.countRecentApiErrors();
    Integer recentTotal = opsQueryMapper.countRecentApiCalls();
    if (recentErrors == null || recentTotal == null || recentTotal == 0) {
      return;
    }
    double errorRate = (double) recentErrors / recentTotal;
    if (errorRate > apiErrorRateThreshold) {
      createAlert("P1", "api-gateway",
          String.format("API error rate %.1f%% exceeds threshold %.1f%% (last 5 min: %d errors / %d requests)",
              errorRate * 100, apiErrorRateThreshold * 100, recentErrors, recentTotal));
    }
  }

  void evaluateDbConnectionPool() {
    try {
      Integer active = opsQueryMapper.countActiveDbConnections();
      if (active != null && active > 40) {
        createAlert("P1", "database", String.format("Database connection pool high: %d active connections", active));
      }
    } catch (Exception e) {
      createAlert("P0", "database", "Database connection check failed: " + e.getMessage());
    }
  }

  void evaluateKnowledgeStaleness() {
    Integer staleCount = opsQueryMapper.countStaleKnowledgeItems(knowledgeStalenessDays);
    if (staleCount != null && staleCount > 0) {
      createAlert("P2", "knowledge-base",
          String.format("%d knowledge items have not been updated in %d+ days", staleCount, knowledgeStalenessDays));
    }
  }

  private void createAlert(String level, String component, String message) {
    Integer existing = opsQueryMapper.countOpenAlertsForComponent(component);
    if (existing != null && existing > 0) {
      log.debug("Alert suppressed (existing OPEN for {}): {}", component, message);
      return;
    }
    opsQueryMapper.insertAlert(level, component, message);
    log.info("Alert created: [{}] {} - {}", level, component, message);
  }
}
