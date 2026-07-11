package com.bizsage.api.recommendations;

import java.util.List;

public final class RecommendationDtos {
  private RecommendationDtos() {
  }

  public record RecommendationItem(
      long id,
      String questionKey,
      String category,
      String questionText,
      double score,
      double topLevelScore,
      long usageCount,
      double ratingAvg,
      long ratingCount,
      String sourceType,
      String sourceRef) {
  }

  public record RecommendationResponse(
      String agentMode,
      String workflowStage,
      boolean refreshAvailable,
      List<RecommendationItem> items) {
  }
}
