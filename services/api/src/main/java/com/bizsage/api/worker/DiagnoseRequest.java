package com.bizsage.api.worker;

import java.util.List;
import java.util.Map;

/**
 * Request body sent to AI worker's POST /agent/diagnose.
 */
public record DiagnoseRequest(
    String question,
    List<Map<String, Object>> knowledge,
    List<Map<String, Object>> recentMessages,
    String conversationSummary,
    List<Map<String, Object>> longTermMemories,
    String regionId,
    String industryId,
    String membershipLevel,
    List<String> conflictLabels) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String question;
    private List<Map<String, Object>> knowledge = List.of();
    private List<Map<String, Object>> recentMessages = List.of();
    private String conversationSummary;
    private List<Map<String, Object>> longTermMemories = List.of();
    private String regionId;
    private String industryId;
    private String membershipLevel = "FREE";
    private List<String> conflictLabels = List.of();

    public Builder question(String question) {
      this.question = question;
      return this;
    }

    public Builder knowledge(List<Map<String, Object>> knowledge) {
      this.knowledge = knowledge;
      return this;
    }

    public Builder recentMessages(List<Map<String, Object>> recentMessages) {
      this.recentMessages = recentMessages;
      return this;
    }

    public Builder conversationSummary(String conversationSummary) {
      this.conversationSummary = conversationSummary;
      return this;
    }

    public Builder longTermMemories(List<Map<String, Object>> longTermMemories) {
      this.longTermMemories = longTermMemories;
      return this;
    }

    public Builder regionId(String regionId) {
      this.regionId = regionId;
      return this;
    }

    public Builder industryId(String industryId) {
      this.industryId = industryId;
      return this;
    }

    public Builder membershipLevel(String membershipLevel) {
      this.membershipLevel = membershipLevel;
      return this;
    }

    public Builder conflictLabels(List<String> conflictLabels) {
      this.conflictLabels = conflictLabels;
      return this;
    }

    public DiagnoseRequest build() {
      return new DiagnoseRequest(
          question, knowledge, recentMessages, conversationSummary,
          longTermMemories, regionId, industryId, membershipLevel, conflictLabels);
    }
  }
}
