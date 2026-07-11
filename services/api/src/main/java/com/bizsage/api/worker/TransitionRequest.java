package com.bizsage.api.worker;

import java.util.List;
import java.util.Map;

public record TransitionRequest(
    String fromMode,
    String toMode,
    String question,
    String chainNodeId,
    List<Map<String, Object>> knowledge,
    List<Map<String, Object>> recentMessages,
    String conversationSummary,
    List<Map<String, Object>> longTermMemories,
    String regionId,
    String industryId,
    String membershipLevel,
    String agentMode,
    String workflowStage,
    List<String> profileMissingFields,
    List<String> recommendationBlacklist) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String fromMode;
    private String toMode;
    private String question;
    private String chainNodeId;
    private List<Map<String, Object>> knowledge = List.of();
    private List<Map<String, Object>> recentMessages = List.of();
    private String conversationSummary;
    private List<Map<String, Object>> longTermMemories = List.of();
    private String regionId;
    private String industryId;
    private String membershipLevel = "FREE";
    private String agentMode = "DIAGNOSIS";
    private String workflowStage = "INTRO";
    private List<String> profileMissingFields = List.of();
    private List<String> recommendationBlacklist = List.of();

    public Builder fromMode(String fromMode) { this.fromMode = fromMode; return this; }
    public Builder toMode(String toMode) { this.toMode = toMode; return this; }
    public Builder question(String question) { this.question = question; return this; }
    public Builder chainNodeId(String chainNodeId) { this.chainNodeId = chainNodeId; return this; }
    public Builder knowledge(List<Map<String, Object>> knowledge) { this.knowledge = knowledge; return this; }
    public Builder recentMessages(List<Map<String, Object>> recentMessages) { this.recentMessages = recentMessages; return this; }
    public Builder conversationSummary(String conversationSummary) { this.conversationSummary = conversationSummary; return this; }
    public Builder longTermMemories(List<Map<String, Object>> longTermMemories) { this.longTermMemories = longTermMemories; return this; }
    public Builder regionId(String regionId) { this.regionId = regionId; return this; }
    public Builder industryId(String industryId) { this.industryId = industryId; return this; }
    public Builder membershipLevel(String membershipLevel) { this.membershipLevel = membershipLevel; return this; }
    public Builder agentMode(String agentMode) { this.agentMode = agentMode; return this; }
    public Builder workflowStage(String workflowStage) { this.workflowStage = workflowStage; return this; }
    public Builder profileMissingFields(List<String> profileMissingFields) { this.profileMissingFields = profileMissingFields; return this; }
    public Builder recommendationBlacklist(List<String> recommendationBlacklist) { this.recommendationBlacklist = recommendationBlacklist; return this; }

    public TransitionRequest build() {
      return new TransitionRequest(fromMode, toMode, question, chainNodeId, knowledge,
          recentMessages, conversationSummary, longTermMemories, regionId, industryId, membershipLevel,
          agentMode, workflowStage, profileMissingFields, recommendationBlacklist);
    }
  }
}
