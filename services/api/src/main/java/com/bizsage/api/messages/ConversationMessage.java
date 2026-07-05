package com.bizsage.api.messages;

import java.time.Instant;

public record ConversationMessage(
    long id,
    long conversationId,
    String sender,
    String messageType,
    String content,
    String sourcesJson,
    String confidence,
    String timeliness,
    String selfCheckStatus,
    boolean activeContext,
    Long summaryGroupId,
    Instant createTime) {
}
