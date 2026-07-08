package com.bizsage.api.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * V2: Lightweight cache hit/miss tracker for measuring cache effectiveness.
 *
 * <p>Exposes per-cache hit-rate statistics for the V2 ops monitoring dashboard.
 * V2 target: aggregate cache hit rate &ge; 70%.
 */
@Component
public class CacheMetrics {

  private static final Logger log = LoggerFactory.getLogger(CacheMetrics.class);

  private final Map<String, AtomicLong> hits = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> misses = new ConcurrentHashMap<>();
  private final CacheManager cacheManager;

  public CacheMetrics(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  /** Record a cache hit for tracking. */
  public void recordHit(String cacheName) {
    hits.computeIfAbsent(cacheName, k -> new AtomicLong()).incrementAndGet();
  }

  /** Record a cache miss for tracking. */
  public void recordMiss(String cacheName) {
    misses.computeIfAbsent(cacheName, k -> new AtomicLong()).incrementAndGet();
  }

  /** Return the hit rate for a specific cache (0.0 – 1.0). Returns -1 if no data. */
  public double hitRate(String cacheName) {
    long h = hits.getOrDefault(cacheName, new AtomicLong()).get();
    long m = misses.getOrDefault(cacheName, new AtomicLong()).get();
    long total = h + m;
    if (total == 0) return -1.0;
    return (double) h / total;
  }

  /**
   * Build a metrics map for all tracked cache regions.
   *
   * <p>Returns a map keyed by cache name, each containing hit, miss, total, and hitRate.
   */
  public Map<String, Object> allMetrics() {
    Map<String, Object> result = new java.util.LinkedHashMap<>();
    for (String name : cacheManager.getCacheNames()) {
      long h = hits.getOrDefault(name, new AtomicLong()).get();
      long m = misses.getOrDefault(name, new AtomicLong()).get();
      long total = h + m;
      result.put(name, Map.of(
          "hits", h,
          "misses", m,
          "total", total,
          "hitRate", total > 0 ? String.format("%.2f", (double) h / total) : "N/A"));
    }
    return result;
  }
}
