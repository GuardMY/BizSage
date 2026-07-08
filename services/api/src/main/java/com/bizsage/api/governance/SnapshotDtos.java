package com.bizsage.api.governance;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for the time-series snapshot service.
 */
public final class SnapshotDtos {

  private SnapshotDtos() {}

  public enum SnapshotType {
    DAILY, WEEKLY, MONTHLY
  }

  public record SnapshotSummary(
      long id,
      String snapshotType,
      String scopeKey,
      String regionId,
      String industryId,
      int retentionDays,
      int recordCount,
      LocalDateTime expiresAt,
      LocalDateTime createTime) {}

  public record SnapshotDetail(
      long id,
      String snapshotType,
      String scopeKey,
      String payloadJson,
      String regionId,
      String industryId,
      int retentionDays,
      int recordCount,
      Long parentSnapshotId,
      LocalDateTime expiresAt,
      LocalDateTime createTime) {}

  public record SnapshotCompareResult(
      long leftSnapshotId,
      long rightSnapshotId,
      List<String> added,
      List<String> removed,
      List<String> changed) {}

  public record SnapshotRetentionPolicy(
      int dailyRetentionDays,
      int weeklyRetentionDays,
      int monthlyRetentionDays) {

    public static final SnapshotRetentionPolicy DEFAULT =
        new SnapshotRetentionPolicy(30, 84, 365);

    public int forType(SnapshotType type) {
      return switch (type) {
        case DAILY -> dailyRetentionDays;
        case WEEKLY -> weeklyRetentionDays;
        case MONTHLY -> monthlyRetentionDays;
      };
    }
  }
}
