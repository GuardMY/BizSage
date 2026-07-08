package com.bizsage.api.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V2: Periodic alert evaluation scheduler.
 *
 * <p>Runs the {@link AlertRuleEngine} on a configurable interval
 * (default every 60 seconds) to evaluate system state against
 * risk-rule thresholds and create alert events.
 */
@Component
public class AlertScheduler {

  private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

  private final AlertRuleEngine engine;

  public AlertScheduler(AlertRuleEngine engine) {
    this.engine = engine;
  }

  @Scheduled(fixedDelayString = "${bizsage.alerts.evaluation-interval-seconds:60}000")
  public void evaluateAlerts() {
    try {
      engine.evaluateAll();
    } catch (Exception e) {
      log.error("Alert evaluation cycle failed", e);
    }
  }
}
