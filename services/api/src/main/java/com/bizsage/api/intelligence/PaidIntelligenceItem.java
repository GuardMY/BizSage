package com.bizsage.api.intelligence;

public record PaidIntelligenceItem(
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
  PaidIntelligenceItem approve() {
    return new PaidIntelligenceItem(
        id,
        title,
        content,
        url,
        "APPROVED",
        confidence,
        linkId,
        regionId,
        industryId,
        sourceId,
        weight,
        entitlement);
  }
}
