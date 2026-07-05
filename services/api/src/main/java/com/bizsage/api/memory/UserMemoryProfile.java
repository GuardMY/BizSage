package com.bizsage.api.memory;

import java.time.Instant;

public record UserMemoryProfile(
    long id,
    long userId,
    String category,
    String key,
    String value,
    String valueType,
    double confidence,
    Long sourceConversationId,
    Long sourceMessageId,
    Instant lastUsedAt,
    Instant expiresAt,
    String status) {
}
