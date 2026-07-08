package com.bizsage.api.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminKnowledgeDtos {
  private AdminKnowledgeDtos() {
  }

  public record KnowledgeTreeNode(
      long nodeId,
      String title,
      String slug,
      String industryId,
      String regionId,
      String linkId,
      String status,
      Long publishedVersionId,
      Integer versionCount,
      LocalDateTime updateTime) {
  }

  public record KnowledgeVersion(
      long versionId,
      long nodeId,
      Integer versionNumber,
      String title,
      String summary,
      String content,
      String sourceUrl,
      String reviewStatus,
      String author,
      String reviewer,
      String reviewNotes,
      String changeNotes,
      BigDecimal confidence,
      String createdByAction,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record KnowledgePublication(
      long publicationId,
      long nodeId,
      long versionId,
      String action,
      String actor,
      String notes,
      LocalDateTime createTime) {
  }

  public record KnowledgeNodeDetail(
      long nodeId,
      String title,
      String slug,
      String industryId,
      String regionId,
      String linkId,
      String status,
      Long draftVersionId,
      Long reviewVersionId,
      Long publishedVersionId,
      List<KnowledgeVersion> versions,
      List<KnowledgePublication> publications) {
  }

  public record KnowledgeVersionDiff(
      long leftVersionId,
      long rightVersionId,
      String leftTitle,
      String rightTitle,
      String leftSummary,
      String rightSummary,
      String leftContent,
      String rightContent,
      String leftSourceUrl,
      String rightSourceUrl,
      BigDecimal leftConfidence,
      BigDecimal rightConfidence,
      String leftReviewStatus,
      String rightReviewStatus) {
  }

  public record KnowledgeDraftRequest(
      Long nodeId,
      String title,
      String slug,
      String industryId,
      String regionId,
      String linkId,
      String summary,
      String content,
      String sourceUrl,
      BigDecimal confidence,
      String changeNotes) {
  }

  public record KnowledgeReviewRequest(String notes) {
  }

  public record KnowledgePublishRequest(String notes) {
  }

  public record KnowledgeRollbackRequest(long targetVersionId, String notes) {
  }

  public record InspectionFinding(
      String type,
      long nodeId,
      String title,
      String detail) {
  }

  public record InspectionReport(
      List<InspectionFinding> findings,
      int totalNodes,
      int healthyNodes,
      int warningNodes,
      int criticalNodes) {
  }
}
