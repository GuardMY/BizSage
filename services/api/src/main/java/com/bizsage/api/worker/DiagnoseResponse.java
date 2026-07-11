package com.bizsage.api.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnoseResponse(
    String answer,
    List<DiagnoseSource> sources,
    String confidence,
    String timeliness,
    @JsonProperty("selfCheckStatus") String selfCheckStatus,
    String disclaimer,
    @JsonProperty("memoryCandidates") List<Map<String, Object>> memoryCandidates,
    String mode,
    @JsonProperty("chainNodeId") String chainNodeId,
    @JsonProperty("suggestedActions") List<String> suggestedActions,
    @JsonProperty("sections") Map<String, String> sections,
    @JsonProperty("recommendationCandidates") List<Map<String, Object>> recommendationCandidates,
    @JsonProperty("currentTopic") String currentTopic,
    @JsonProperty("nextBestTopics") List<String> nextBestTopics,
    @JsonProperty("workflowStage") String workflowStage,
    @JsonProperty("profileMissingFields") List<String> profileMissingFields,
    @JsonProperty("completionSignal") String completionSignal,
    @JsonProperty("recommendedQuestions") List<Map<String, Object>> recommendedQuestions) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DiagnoseSource(
      String id,
      String title,
      @JsonProperty("sourceUrl") String sourceUrl,
      @JsonProperty("sourceId") String sourceId,
      double confidence,
      Double score,
      String entitlement) {
  }

  public boolean isSuccessful() {
    return answer != null && !answer.isBlank()
        && !"LLM_NOT_CONFIGURED".equals(selfCheckStatus)
        && !"LLM_CALL_FAILED".equals(selfCheckStatus);
  }
}
