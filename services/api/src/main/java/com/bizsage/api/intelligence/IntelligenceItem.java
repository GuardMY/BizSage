package com.bizsage.api.intelligence;

public record IntelligenceItem(
    long id,
    String title,
    String content,
    String url,
    String status,
    double confidence,
    String linkId,
    String regionId,
    String industryId,
    String sourceId,
    double weight,
    String entitlement) {

  /** Shortcut for FREE-tier intelligence (backward-compatible). */
  public IntelligenceItem(long id, String title, String content, String url, String status,
                          double confidence, String linkId, String regionId, String industryId,
                          String sourceId, double weight) {
    this(id, title, content, url, status, confidence, linkId, regionId, industryId, sourceId, weight, "FREE");
  }

  IntelligenceItem approve() {
    return new IntelligenceItem(id, title, content, url, "APPROVED", confidence, linkId, regionId,
        industryId, sourceId, weight, entitlement);
  }
}
