package com.bizsage.api.governance;

import com.bizsage.api.governance.SnapshotDtos.SnapshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for automatic snapshot generation and retention enforcement.
 */
@Component
public class SnapshotScheduler {

  private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

  private final SnapshotService service;
  private final SnapshotStore store;

  public SnapshotScheduler(SnapshotService service, SnapshotStore store) {
    this.service = service;
    this.store = store;
  }

  /** Daily at 02:00 — snapshot all active intelligence. */
  @Scheduled(cron = "0 0 2 * * *")
  public void generateDailySnapshots() {
    log.info("Starting daily snapshot generation");
    service.generateAllScopes(SnapshotType.DAILY);
    log.info("Daily snapshot generation complete");
  }

  /** Weekly Sunday at 03:00 — aggregated weekly summaries. */
  @Scheduled(cron = "0 0 3 * * SUN")
  public void generateWeeklySnapshots() {
    log.info("Starting weekly snapshot generation");
    service.generateAllScopes(SnapshotType.WEEKLY);
    log.info("Weekly snapshot generation complete");
  }

  /** Monthly 1st at 04:00 — monthly report data. */
  @Scheduled(cron = "0 0 4 1 * *")
  public void generateMonthlySnapshots() {
    log.info("Starting monthly snapshot generation");
    service.generateAllScopes(SnapshotType.MONTHLY);
    log.info("Monthly snapshot generation complete");
  }

  /** Hourly cleanup of expired snapshots. */
  @Scheduled(cron = "0 0 * * * *")
  public void cleanExpiredSnapshots() {
    int deleted = store.deleteExpired();
    if (deleted > 0) {
      log.info("Cleaned {} expired snapshots", deleted);
    }
  }
}
