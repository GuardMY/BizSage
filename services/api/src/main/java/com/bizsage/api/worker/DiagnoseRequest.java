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
    List<String> conflictLabels,
    String agentMode,
    String workflowStage,
    List<String> profileMissingFields,
    List<String> recommendedQuestionIds,
    Boolean diagnosisClosable) {

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
    private String agentMode = "DIAGNOSIS";
    private String workflowStage = "INTRO";
    private List<String> profileMissingFields = List.of();
    private List<String> recommendedQuestionIds = List.of();
    private Boolean diagnosisClosable = Boolean.FALSE;

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

    public Builder agentMode(String agentMode) {
      this.agentMode = agentMode;
      return this;
    }

    public Builder workflowStage(String workflowStage) {
      this.workflowStage = workflowStage;
      return this;
    }

    public Builder profileMissingFields(List<String> profileMissingFields) {
      this.profileMissingFields = profileMissingFields;
      return this;
    }

    public Builder recommendedQuestionIds(List<String> recommendedQuestionIds) {
      this.recommendedQuestionIds = recommendedQuestionIds;
      return this;
    }

    public Builder diagnosisClosable(Boolean diagnosisClosable) {
      this.diagnosisClosable = diagnosisClosable;
      return this;
    }

    public DiagnoseRequest build() {
      return new DiagnoseRequest(
          question, knowledge, recentMessages, conversationSummary,
          longTermMemories, regionId, industryId, membershipLevel, conflictLabels,
          agentMode, workflowStage, profileMissingFields, recommendedQuestionIds, diagnosisClosable);
    }
  }
}
