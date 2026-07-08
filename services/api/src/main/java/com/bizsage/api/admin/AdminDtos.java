package com.bizsage.api.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AdminDtos {
  private AdminDtos() {
  }

  public record Dashboard(
      List<Metric> metrics,
      List<AlertItem> urgentAlerts,
      List<TicketItem> openTickets,
      List<IntelligenceReviewItem> pendingReviews,
      List<AuditLogItem> recentAuditLogs) {
  }

  public record Metric(String key, String label, String value, String status, String detail) {
  }

  public record AlertItem(
      long id,
      String level,
      String component,
      String message,
      String status,
      String owner,
      String regionId,
      String industryId,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record AuditLogItem(
      long id,
      String actor,
      String action,
      String targetType,
      String targetId,
      String result,
      String regionId,
      String industryId,
      LocalDateTime createTime) {
  }

  public record IntelligenceReviewItem(
      long id,
      long intelligenceId,
      String title,
      String content,
      String url,
      String status,
      String reviewStatus,
      String verdict,
      String reviewer,
      String reason,
      BigDecimal confidence,
      String regionId,
      String industryId,
      String sourceId,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record TicketItem(
      long id,
      String ticketType,
      String severity,
      String targetType,
      long targetId,
      String title,
      String status,
      String owner,
      String nextAction,
      String regionId,
      String industryId,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record HumanIntelligenceItem(
      long id,
      String city,
      String industryId,
      String linkId,
      String content,
      String sourceType,
      String collector,
      String eventTime,
      BigDecimal confidence,
      String entitlement,
      String status,
      String reviewer,
      String reviewNotes,
      String regionId,
      String sourceId,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
  }

  public record AlertActionRequest(String notes) {
  }

  public record ReviewVerdictRequest(String verdict, String notes) {
  }

  public record TicketTransitionRequest(String status, String owner, String nextAction, String notes) {
  }

  public record HumanIntelligenceCreateRequest(
      String city,
      String industryId,
      String linkId,
      String content,
      String sourceType,
      String collector,
      String eventTime,
      BigDecimal confidence,
      String entitlement,
      String regionId,
      String sourceId) {
  }

  public record HumanIntelligenceReviewRequest(String verdict, String notes) {
  }

  public record AdminList<T>(List<T> items, int total, Map<String, Object> summary) {
  }
}
