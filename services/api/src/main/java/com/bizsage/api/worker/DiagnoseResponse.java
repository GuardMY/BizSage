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
    @JsonProperty("recommendedQuestions") List<Map<String, Object>> recommendedQuestions,
    @JsonProperty("userProfileMemories") List<Map<String, Object>> userProfileMemories,
    @JsonProperty("diagnosisMemories") List<Map<String, Object>> diagnosisMemories,
    @JsonProperty("diagnosisCompleteness") Double diagnosisCompleteness,
    @JsonProperty("diagnosisMissingFields") List<String> diagnosisMissingFields,
    @JsonProperty("additionalInformationQuestions") List<Map<String, Object>> additionalInformationQuestions,
  @JsonProperty("reportReady") Boolean reportReady) {

  public DiagnoseResponse(
      String answer, List<DiagnoseSource> sources, String confidence, String timeliness,
      String selfCheckStatus, String disclaimer, List<Map<String, Object>> memoryCandidates,
      String mode, String chainNodeId, List<String> suggestedActions,
      Map<String, String> sections, List<Map<String, Object>> recommendationCandidates,
      String currentTopic, List<String> nextBestTopics, String workflowStage,
      List<String> profileMissingFields, String completionSignal,
      List<Map<String, Object>> recommendedQuestions) {
    this(answer, sources, confidence, timeliness, selfCheckStatus, disclaimer, memoryCandidates,
        mode, chainNodeId, suggestedActions, sections, recommendationCandidates, currentTopic,
        nextBestTopics, workflowStage, profileMissingFields, completionSignal, recommendedQuestions,
        null, null, null, null, null, null);
  }

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
