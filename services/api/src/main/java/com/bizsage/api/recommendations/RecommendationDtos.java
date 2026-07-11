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

  public record RecommendationUpsertRequest(
      String industryId,
      String regionId,
      String agentMode,
      String questionKey,
      String category,
      String questionText,
      Double topLevelScore,
      String status,
      String sourceType,
      String sourceRef) {
  }

  public record RatingRequest(
      Double rating) {
  }

  public record UsageRequest(
      List<Long> ids) {
  }
}
