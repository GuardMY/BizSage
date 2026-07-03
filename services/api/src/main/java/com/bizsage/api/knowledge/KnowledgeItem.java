package com.bizsage.api.knowledge;

public record KnowledgeItem(
    long id,
    String title,
    String content,
    String industryId,
    String regionId,
    String linkId,
    String sourceId,
    double confidence,
    double weight) {
}
