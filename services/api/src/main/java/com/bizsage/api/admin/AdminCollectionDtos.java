package com.bizsage.api.admin;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminCollectionDtos {
  private AdminCollectionDtos() {
  }

  public record CollectionSourceConfig(
      long sourceConfigId,
      String name,
      String sourceType,
      String status,
      Integer intervalMinutes,
      Integer maxRetries,
      Integer failureThreshold,
      Integer cooldownMinutes,
      String circuitState,
      Integer failureCount,
      String regionId,
      String industryId,
      String linkId,
      String sourceId,
      String payloadJson,
      LocalDateTime nextRunTime,
      LocalDateTime lastRunTime,
      String lastStatus,
      String lastError,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record CollectionKeyword(
      long keywordId,
      long sourceConfigId,
      String keyword,
      String matchMode,
      String status,
      String notes,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record CollectionJobRun(
      long runId,
      long jobId,
      long sourceConfigId,
      String sourceName,
      String sourceType,
      String triggerType,
      String status,
      Integer queueDepth,
      Integer retryCount,
      String circuitState,
      Integer recordsCollected,
      Integer recordsFiltered,
      Integer recordsPersisted,
      String errorMessage,
      LocalDateTime startTime,
      LocalDateTime finishTime) {
  }

  public record CollectionDeadLetter(
      long deadLetterId,
      Long jobId,
      String sourceName,
      String reason,
      String errorMessage,
      String payloadJson,
      LocalDateTime createTime) {
  }

  public record CollectionSourceDetail(
      CollectionSourceConfig source,
      List<CollectionKeyword> keywords,
      List<CollectionJobRun> recentRuns) {
  }

  public record CollectionSourceUpsertRequest(
      Long sourceConfigId,
      String name,
      String sourceType,
      String status,
      Integer intervalMinutes,
      Integer maxRetries,
      Integer failureThreshold,
      Integer cooldownMinutes,
      String regionId,
      String industryId,
      String linkId,
      String sourceId,
      String payloadJson) {
  }

  public record CollectionKeywordUpsertRequest(
      Long keywordId,
      Long sourceConfigId,
      String keyword,
      String matchMode,
      String status,
      String notes) {
  }
}
