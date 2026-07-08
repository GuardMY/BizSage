package com.bizsage.api.ops;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * V2: Persistence for SLA data points.
 *
 * <p>Each data point records a 1-minute aggregation window of request
 * counts, error counts, and latency values.
 */
@Repository
public class SlaStore {

  private final JdbcTemplate jdbc;

  public SlaStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Ensure the sla_data_points table exists (idempotent). */
  public void ensureSchema() {
    jdbc.execute("""
        create table if not exists sla_data_points (
          id bigint primary key auto_increment,
          window_start timestamp not null,
          window_end timestamp not null,
          total_requests int not null default 0,
          error_requests int not null default 0,
          latency_p50_ms double null,
          latency_p95_ms double null,
          latency_p99_ms double null,
          uptime_flag tinyint not null default 1,
          create_time timestamp not null default current_timestamp
        )
        """);
  }

  /** Insert an aggregated data point. */
  public void insert(SlaDataPoint point) {
    jdbc.update("""
        insert into sla_data_points
          (window_start, window_end, total_requests, error_requests,
           latency_p50_ms, latency_p95_ms, latency_p99_ms, uptime_flag)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        Timestamp.from(point.windowStart()),
        Timestamp.from(point.windowEnd()),
        point.totalRequests(),
        point.errorRequests(),
        point.latencyP50Ms(),
        point.latencyP95Ms(),
        point.latencyP99Ms(),
        point.uptimeFlag() ? 1 : 0);
  }

  /** Query data points within a time window. */
  public List<Map<String, Object>> queryWindow(Instant since, Instant until) {
    return jdbc.queryForList("""
        select window_start, window_end, total_requests, error_requests,
               latency_p50_ms, latency_p95_ms, latency_p99_ms, uptime_flag
          from sla_data_points
         where window_start >= ? and window_start <= ?
         order by window_start
        """, Timestamp.from(since), Timestamp.from(until));
  }

  /** Delete data points older than retention period. */
  public int deleteOlderThan(Instant cutoff) {
    return jdbc.update("delete from sla_data_points where window_start < ?",
        Timestamp.from(cutoff));
  }

  /** SLA data point record. */
  record SlaDataPoint(
      Instant windowStart,
      Instant windowEnd,
      int totalRequests,
      int errorRequests,
      Double latencyP50Ms,
      Double latencyP95Ms,
      Double latencyP99Ms,
      boolean uptimeFlag) {
  }
}
