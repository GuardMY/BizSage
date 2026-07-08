package com.bizsage.api.knowledge;

public record KnowledgeItem(
    long id,
    String title,
    String content,
    String industryId,
    String regionId,
    String linkId,
    String sourceId,
    String sourceUrl,
    double confidence,
    double weight,
    String entitlement) {

  /** Shortcut for FREE-tier knowledge (backward-compatible). */
  public KnowledgeItem(long id, String title, String content, String industryId,
                       String regionId, String linkId, String sourceId, String sourceUrl,
                       double confidence, double weight) {
    this(id, title, content, industryId, regionId, linkId, sourceId, sourceUrl, confidence, weight, "FREE");
  }
}
