package com.bizsage.api.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Structured response from AI worker's POST /agent/diagnose.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnoseResponse(
    String answer,
    List<DiagnoseSource> sources,
    String confidence,
    String timeliness,
    @JsonProperty("selfCheckStatus") String selfCheckStatus,
    String disclaimer,
    @JsonProperty("memoryCandidates") List<Map<String, Object>> memoryCandidates,
    // ── Learning Agent fields (null for diagnosis-only responses) ──
    String mode,
    @JsonProperty("chainNodeId") String chainNodeId,
    @JsonProperty("suggestedActions") List<String> suggestedActions,
    @JsonProperty("sections") Map<String, String> sections) {

  /**
   * Worker source entry — maps to the sources array in the worker response.
   */
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

  /**
   * Returns true when this response represents a successful diagnosis
   * (not an error state from the worker).
   */
  public boolean isSuccessful() {
    return answer != null && !answer.isBlank()
        && !"LLM_NOT_CONFIGURED".equals(selfCheckStatus)
        && !"LLM_CALL_FAILED".equals(selfCheckStatus);
  }
}
