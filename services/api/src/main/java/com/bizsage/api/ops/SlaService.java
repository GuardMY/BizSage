package com.bizsage.api.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * V2: SLA statistics service.
 *
 * <p>Computes uptime percentage, error rate, and latency percentiles
 * over configurable time windows (1h, 24h, 7d).
 *
 * <p>SLI definitions:
 * <ul>
 *   <li><b>Uptime</b> — percentage of 1-minute windows where {@code uptime_flag = 1}.</li>
 *   <li><b>Error rate</b> — total error requests / total requests.</li>
 *   <li><b>Latency P50/P95/P99</b> — aggregated from per-minute data points.</li>
 * </ul>
 */
@Service
public class SlaService {

  private static final Logger log = LoggerFactory.getLogger(SlaService.class);

  private final SlaStore store;

  public SlaService(SlaStore store) {
    this.store = store;
  }

  /**
   * Compute SLA statistics for the given window.
   *
   * @param window One of "1h", "24h", "7d".
   */
  public Map<String, Object> computeSla(String window) {
    Duration duration = parseWindow(window);
    Instant now = Instant.now();
    Instant since = now.minus(duration);

    List<Map<String, Object>> points = store.queryWindow(since, now);
    if (points.isEmpty()) {
      return emptySlaResponse(window);
    }

    long totalRequests = 0;
    long totalErrors = 0;
    int uptimeWindows = 0;
    int totalWindows = points.size();

    // Collect all P50 values for computation
    double[] p50s = new double[points.size()];
    int i = 0;

    for (Map<String, Object> point : points) {
      int req = intVal(point, "total_requests");
      int err = intVal(point, "error_requests");
      boolean up = intVal(point, "uptime_flag") == 1;

      totalRequests += req;
      totalErrors += err;
      if (up) uptimeWindows++;
      p50s[i++] = doubleVal(point, "latency_p50_ms");
    }

    double uptime = totalWindows > 0 ? (double) uptimeWindows / totalWindows : 1.0;
    double errorRate = totalRequests > 0 ? (double) totalErrors / totalRequests : 0.0;

    // Sort for percentile calculation
    java.util.Arrays.sort(p50s);
    double p50 = percentile(p50s, 0.50);
    double p95 = percentile(p50s, 0.95);
    double p99 = percentile(p50s, 0.99);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("window", window);
    result.put("from", since.toString());
    result.put("to", now.toString());
    result.put("dataPoints", totalWindows);
    result.put("uptime", String.format("%.4f", uptime));
    result.put("uptimePercent", String.format("%.2f%%", uptime * 100));
    result.put("errorRate", String.format("%.4f", errorRate));
    result.put("errorRatePercent", String.format("%.2f%%", errorRate * 100));
    result.put("latencyP50Ms", String.format("%.2f", p50));
    result.put("latencyP95Ms", String.format("%.2f", p95));
    result.put("latencyP99Ms", String.format("%.2f", p99));
    result.put("targetUptime", "0.999");
    result.put("slaMet", uptime >= 0.999);
    return result;
  }

  private Duration parseWindow(String window) {
    return switch (window) {
      case "1h" -> Duration.ofHours(1);
      case "24h" -> Duration.ofHours(24);
      case "7d" -> Duration.ofDays(7);
      default -> Duration.ofHours(24);
    };
  }

  private Map<String, Object> emptySlaResponse(String window) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("window", window);
    result.put("from", Instant.now().minus(parseWindow(window)).toString());
    result.put("to", Instant.now().toString());
    result.put("dataPoints", 0);
    result.put("uptime", "N/A");
    result.put("message", "No SLA data available yet. Wait for the aggregation cycle to collect data.");
    return result;
  }

  private double percentile(double[] sorted, double p) {
    if (sorted.length == 0) return 0.0;
    int index = (int) Math.ceil(p * sorted.length) - 1;
    if (index < 0) index = 0;
    if (index >= sorted.length) index = sorted.length - 1;
    return sorted[index];
  }

  private int intVal(Map<String, Object> row, String key) {
    Object v = row.get(key);
    if (v instanceof Number n) return n.intValue();
    return 0;
  }

  private double doubleVal(Map<String, Object> row, String key) {
    Object v = row.get(key);
    if (v instanceof Number n) return n.doubleValue();
    return 0.0;
  }
}
