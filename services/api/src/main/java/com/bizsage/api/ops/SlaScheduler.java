package com.bizsage.api.ops;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V2: SLA data aggregation scheduler.
 *
 * <p>Runs every minute to aggregate request metrics into an
 * {@code sla_data_points} row. Also enforces data retention
 * by deleting points older than the configured retention period.
 */
@Component
public class SlaScheduler {

  private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

  private final JdbcTemplate jdbc;
  private final SlaStore store;
  private final int retentionDays;

  public SlaScheduler(
      JdbcTemplate jdbc,
      SlaStore store,
      @Value("${bizsage.sla.retention-days:90}") int retentionDays) {
    this.jdbc = jdbc;
    this.store = store;
    this.retentionDays = retentionDays;
    store.ensureSchema();
  }

  /** Aggregate the previous minute's data and insert a new data point. */
  @Scheduled(fixedDelayString = "${bizsage.sla.aggregation-window-minutes:1}0000")
  public void aggregate() {
    Instant now = Instant.now();
    Instant windowStart = now.minus(Duration.ofMinutes(1));
    Instant windowEnd = now;

    try {
      int total = countByResult("SUCCESS", windowStart, windowEnd)
          + countByResult("ERROR", windowStart, windowEnd);
      int errors = countByResult("ERROR", windowStart, windowEnd);

      store.insert(new SlaStore.SlaDataPoint(
          windowStart, windowEnd, total, errors,
          null, null, null, true));
    } catch (Exception e) {
      log.warn("SLA aggregation cycle skipped: {}", e.getMessage());
    }

    // Hourly cleanup of expired data
    if (System.currentTimeMillis() % (3_600_000) < 60_000) {
      int deleted = store.deleteOlderThan(now.minus(Duration.ofDays(retentionDays)));
      if (deleted > 0) log.debug("Cleaned {} expired SLA data points", deleted);
    }
  }

  private int countByResult(String result, Instant since, Instant until) {
    Integer count = jdbc.queryForObject(
        "select count(*) from audit_logs where result = ? and create_time >= ? and create_time < ?",
        Integer.class, result, Timestamp.from(since), Timestamp.from(until));
    return count != null ? count : 0;
  }
}
