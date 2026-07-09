package com.bizsage.api.ops;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaScheduler {
  private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

  private final OpsQueryMapper opsQueryMapper;
  private final SlaStore store;
  private final int retentionDays;

  public SlaScheduler(
      OpsQueryMapper opsQueryMapper,
      SlaStore store,
      @Value("${bizsage.sla.retention-days:90}") int retentionDays) {
    this.opsQueryMapper = opsQueryMapper;
    this.store = store;
    this.retentionDays = retentionDays;
  }

  @Scheduled(fixedDelayString = "${bizsage.sla.aggregation-window-minutes:1}0000")
  public void aggregate() {
    Instant now = Instant.now();
    Instant windowStart = now.minus(Duration.ofMinutes(1));
    Instant windowEnd = now;

    try {
      int success = countByResult("SUCCESS", windowStart, windowEnd);
      int errors = countByResult("ERROR", windowStart, windowEnd);
      store.insert(new SlaStore.SlaDataPoint(windowStart, windowEnd, success + errors, errors, null, null, null, true));
    } catch (Exception e) {
      log.warn("SLA aggregation cycle skipped: {}", e.getMessage());
    }

    if (System.currentTimeMillis() % 3_600_000 < 60_000) {
      int deleted = store.deleteOlderThan(now.minus(Duration.ofDays(retentionDays)));
      if (deleted > 0) {
        log.debug("Cleaned {} expired SLA data points", deleted);
      }
    }
  }

  private int countByResult(String result, Instant since, Instant until) {
    Integer count = opsQueryMapper.countAuditLogsByResult(result, since, until);
    return count == null ? 0 : count;
  }
}
