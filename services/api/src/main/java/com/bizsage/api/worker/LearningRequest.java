package com.bizsage.api.worker;

import java.util.List;
import java.util.Map;

/**
 * Request body sent to AI worker's POST /agent/learn.
 * Mirrors the Python LearnRequest model.
 */
public record LearningRequest(
    String question,
    List<Map<String, Object>> knowledge,
    String chainNodeId,
    String learningMode,
    List<Map<String, Object>> recentMessages,
    String conversationSummary,
    List<Map<String, Object>> longTermMemories,
    String regionId,
    String industryId,
    String membershipLevel) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String question;
    private List<Map<String, Object>> knowledge = List.of();
    private String chainNodeId;
    private String learningMode = "FAST_START";
    private List<Map<String, Object>> recentMessages = List.of();
    private String conversationSummary;
    private List<Map<String, Object>> longTermMemories = List.of();
    private String regionId;
    private String industryId;
    private String membershipLevel = "FREE";

    public Builder question(String question) { this.question = question; return this; }
    public Builder knowledge(List<Map<String, Object>> knowledge) { this.knowledge = knowledge; return this; }
    public Builder chainNodeId(String chainNodeId) { this.chainNodeId = chainNodeId; return this; }
    public Builder learningMode(String learningMode) { this.learningMode = learningMode; return this; }
    public Builder recentMessages(List<Map<String, Object>> recentMessages) { this.recentMessages = recentMessages; return this; }
    public Builder conversationSummary(String conversationSummary) { this.conversationSummary = conversationSummary; return this; }
    public Builder longTermMemories(List<Map<String, Object>> longTermMemories) { this.longTermMemories = longTermMemories; return this; }
    public Builder regionId(String regionId) { this.regionId = regionId; return this; }
    public Builder industryId(String industryId) { this.industryId = industryId; return this; }
    public Builder membershipLevel(String membershipLevel) { this.membershipLevel = membershipLevel; return this; }

    public LearningRequest build() {
      return new LearningRequest(question, knowledge, chainNodeId, learningMode,
          recentMessages, conversationSummary, longTermMemories, regionId, industryId, membershipLevel);
    }
  }
}
