package com.bizsage.api.conversations;

public record Conversation(
    long id,
    String ownerUsername,
    String title,
    String status,
    String regionId,
    String industryId,
    String sourceId,
    double weight) {
  Conversation archive() {
    return new Conversation(id, ownerUsername, title, "ARCHIVED", regionId, industryId, sourceId, weight);
  }
}
