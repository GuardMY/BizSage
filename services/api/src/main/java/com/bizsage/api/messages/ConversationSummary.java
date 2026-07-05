package com.bizsage.api.messages;

public record ConversationSummary(
    long id,
    long conversationId,
    String summaryText,
    long coveredMessageStartId,
    long coveredMessageEndId,
    int summaryVersion,
    boolean active) {
}
