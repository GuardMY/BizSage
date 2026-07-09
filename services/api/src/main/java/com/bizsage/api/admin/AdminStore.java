package com.bizsage.api.admin;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bizsage.api.admin.AdminDtos.AdminList;
import com.bizsage.api.admin.AdminDtos.AlertItem;
import com.bizsage.api.admin.AdminDtos.AuditLogItem;
import com.bizsage.api.admin.AdminDtos.Dashboard;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceCreateRequest;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceItem;
import com.bizsage.api.admin.AdminDtos.IntelligenceReviewItem;
import com.bizsage.api.admin.AdminDtos.Metric;
import com.bizsage.api.admin.AdminDtos.RiskRule;
import com.bizsage.api.admin.AdminDtos.RiskRuleUpsertRequest;
import com.bizsage.api.admin.AdminDtos.TicketItem;
import com.bizsage.api.intelligence.IntelligenceItem;
import com.bizsage.api.intelligence.IntelligenceMapper;
import com.bizsage.api.intelligence.ReviewTicket;
import com.bizsage.api.intelligence.AdminReviewMapper;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminStore {
  private final AdminStoreMapper mapper;
  private final IntelligenceMapper intelligenceMapper;
  private final AdminReviewMapper reviewMapper;

  public AdminStore(AdminStoreMapper mapper, IntelligenceMapper intelligenceMapper, AdminReviewMapper reviewMapper) {
    this.mapper = mapper;
    this.intelligenceMapper = intelligenceMapper;
    this.reviewMapper = reviewMapper;
  }

  Dashboard dashboard() {
    List<Metric> metrics = List.of(
        new Metric("pendingReviews", "Pending reviews", String.valueOf(intValue(mapper.countPendingReviews())), "warning", "Intelligence waiting for reviewer verdict"),
        new Metric("openTickets", "Open tickets", String.valueOf(intValue(mapper.countOpenTickets())), "warning", "Operational ledger items still active"),
        new Metric("openAlerts", "Open alerts", String.valueOf(intValue(mapper.countOpenAlerts())), "critical", "Alerts that still require acknowledgement"),
        new Metric("auditLogs", "Audit logs", String.valueOf(intValue(mapper.countAuditLogs())), "healthy", "Recorded administrator operations"),
        new Metric("humanIntel", "Human intelligence", String.valueOf(intValue(mapper.countHumanIntelligence())), "healthy", "Submitted local intelligence records"),
        new Metric("p0Alerts", "P0 alerts", String.valueOf(intValue(mapper.countOpenP0Alerts())), "critical", "Highest severity open alerts"),
        new Metric("collectionSources", "Collection sources", String.valueOf(intValue(mapper.countEnabledCollectionSources())), "healthy", "Active data source configurations"),
        new Metric("collectionSuccessRate", collectionSuccessRate(), collectionSuccessRate(), "healthy", "Recent collection run success rate"),
        new Metric("knowledgeNodes", "Knowledge nodes", String.valueOf(intValue(mapper.countKnowledgeNodes())), "healthy", "Maintained knowledge records"),
        new Metric("crawlerHealth", "Crawler circuits", crawlerHealth(), "healthy", "Open circuit breaker states"));

    return new Dashboard(
        metrics,
        listAlerts("OPEN").items().stream().limit(5).toList(),
        listTickets(null).items().stream().limit(5).toList(),
        listReviews("PENDING").items().stream().limit(5).toList(),
        listAuditLogs(null).items().stream().limit(5).toList());
  }

  Map<String, Object> getCollectionTelemetry() {
    int totalRuns24h = intValue(mapper.countCollectionRuns24h());
    int successRuns24h = intValue(mapper.countSuccessfulCollectionRuns24h());
    double successRate24h = totalRuns24h > 0 ? (double) successRuns24h / totalRuns24h : -1.0;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalSources", intValue(mapper.countCollectionSources()));
    result.put("enabledSources", intValue(mapper.countEnabledCollectionSources()));
    result.put("openCircuits", intValue(mapper.countOpenCollectionCircuits()));
    result.put("deadLetterCount", intValue(mapper.countDeadLetters()));
    result.put("totalRuns24h", totalRuns24h);
    result.put("successRate24h", String.format("%.1f%%", successRate24h * 100));
    result.put("recordsCollected24h", intValue(mapper.sumCollectedRecords24h()));
    result.put("recentRuns", mapper.listRecentCollectionRuns());
    return result;
  }

  AdminList<AlertItem> listAlerts(String status) {
    List<AlertItem> items = mapper.listAlerts(status).stream().map(this::toAlertItem).toList();
    return new AdminList<>(items, items.size(), Map.of(
        "open", intValue(mapper.countOpenAlerts()),
        "p0", intValue(mapper.countOpenP0Alerts())));
  }

  AlertItem updateAlert(long id, String action, String actor, String notes) {
    String status = switch (action) {
      case "acknowledge" -> "ACKNOWLEDGED";
      case "claim" -> "CLAIMED";
      case "close" -> "CLOSED";
      default -> throw new IllegalArgumentException("unsupported alert action");
    };
    if (mapper.updateAlert(id, status, actor) == 0) {
      throw new IllegalArgumentException("alert not found");
    }
    writeAudit(actor, "ADMIN_ALERT_" + action.toUpperCase(), "alert", String.valueOf(id), "SUCCESS", "global", "global");
    return findAlert(id);
  }

  AdminList<AuditLogItem> listAuditLogs(String query) {
    String like = "%" + (query == null ? "" : query.trim()) + "%";
    List<AuditLogItem> items = mapper.listAuditLogs(like).stream().map(this::toAuditLogItem).toList();
    return new AdminList<>(items, items.size(), Map.of("total", items.size()));
  }

  AdminList<IntelligenceReviewItem> listReviews(String status) {
    List<IntelligenceReviewItem> items = mapper.listReviews(status).stream().map(this::toReviewItem).toList();
    return new AdminList<>(items, items.size(), Map.of(
        "pending", intValue(mapper.countPendingReviews()),
        "completed", intValue(mapper.countCompletedReviews())));
  }

  IntelligenceReviewItem decideReview(long id, String verdict, String notes, String actor) {
    String normalizedVerdict = normalizeVerdict(verdict);
    if (mapper.completeReview(id, normalizedVerdict, actor, notes) == 0) {
      throw new IllegalArgumentException("review not found");
    }

    IntelligenceReviewItem item = findReview(id);
    String intelligenceStatus = switch (normalizedVerdict) {
      case "PASS" -> "APPROVED";
      case "REJECT" -> "REJECTED";
      case "FLAG" -> "NEEDS_REVIEW";
      case "SUSPICIOUS" -> "SUSPICIOUS";
      case "COMPLIANCE" -> "COMPLIANCE_HOLD";
      case "PAID_INTEL" -> "PAID_REVIEW";
      case "ARCHIVE" -> "ARCHIVED";
      default -> "PENDING";
    };

    intelligenceMapper.update(null, new LambdaUpdateWrapper<IntelligenceItem>()
        .eq(IntelligenceItem::getId, item.intelligenceId())
        .set(IntelligenceItem::getStatus, intelligenceStatus));

    if ("FLAG".equals(normalizedVerdict) || "SUSPICIOUS".equals(normalizedVerdict)) {
      String ticketType = "FLAG".equals(normalizedVerdict) ? "review_escalation" : "suspicious_review";
      Map<String, Object> ticket = new LinkedHashMap<>();
      ticket.put("ticketType", ticketType);
      ticket.put("severity", "P1");
      ticket.put("targetType", "intelligence");
      ticket.put("targetId", item.intelligenceId());
      ticket.put("title", normalizedVerdict.equals("SUSPICIOUS") ? "Suspicious intelligence review: " + item.title() : "Escalated intelligence review: " + item.title());
      ticket.put("description", notes);
      ticket.put("status", "NEW");
      ticket.put("owner", actor);
      ticket.put("nextAction", "FLAG".equals(normalizedVerdict) ? "Assign second reviewer and confirm source conflict." : "Investigate suspicious signal and verify evidence.");
      ticket.put("regionId", item.regionId());
      ticket.put("industryId", item.industryId());
      mapper.insertTicket(ticket);
    }
    if ("COMPLIANCE".equals(normalizedVerdict)) {
      Map<String, Object> ticket = new LinkedHashMap<>();
      ticket.put("ticketType", "compliance_review");
      ticket.put("severity", "P1");
      ticket.put("targetType", "intelligence");
      ticket.put("targetId", item.intelligenceId());
      ticket.put("title", "Compliance review: " + item.title());
      ticket.put("description", notes);
      ticket.put("status", "NEW");
      ticket.put("owner", actor);
      ticket.put("nextAction", "Route to legal/compliance for content review.");
      ticket.put("regionId", item.regionId());
      ticket.put("industryId", item.industryId());
      mapper.insertTicket(ticket);
    }
    if ("PAID_INTEL".equals(normalizedVerdict)) {
      intelligenceMapper.update(null, new LambdaUpdateWrapper<IntelligenceItem>()
          .eq(IntelligenceItem::getId, item.intelligenceId())
          .set(IntelligenceItem::getEntitlement, "PAID"));
    }

    ReviewTicket ticket = reviewMapper.selectById(id);
    if (ticket != null) {
      ticket.setReviewStatus("COMPLETED");
      ticket.setVerdict(normalizedVerdict);
      ticket.setReviewer(actor);
      ticket.setReason(notes);
      reviewMapper.updateById(ticket);
    }

    writeAudit(actor, "ADMIN_INTELLIGENCE_" + normalizedVerdict, "intelligence", String.valueOf(item.intelligenceId()), "SUCCESS", item.regionId(), item.industryId());
    return findReview(id);
  }

  AdminList<TicketItem> listTickets(String status) {
    List<TicketItem> items = mapper.listTickets(status).stream().map(this::toTicketItem).toList();
    return new AdminList<>(items, items.size(), Map.of(
        "open", intValue(mapper.countOpenTickets()),
        "p0", intValue(mapper.countP0Tickets())));
  }

  TicketItem transitionTicket(long id, String status, String owner, String nextAction, String actor, String notes) {
    if (mapper.transitionTicket(id, blankToDefault(status, "IN_PROGRESS"), blankToDefault(owner, actor), blankToDefault(nextAction, "Continue handling")) == 0) {
      throw new IllegalArgumentException("ticket not found");
    }
    TicketItem item = findTicket(id);
    writeAudit(actor, "ADMIN_TICKET_TRANSITION", "ticket", String.valueOf(id), "SUCCESS", item.regionId(), item.industryId());
    return item;
  }

  AdminList<HumanIntelligenceItem> listHumanIntelligence(String status) {
    List<HumanIntelligenceItem> items = mapper.listHumanIntelligence(status).stream().map(this::toHumanItem).toList();
    return new AdminList<>(items, items.size(), Map.of(
        "pending", intValue(mapper.countPendingHumanIntelligence()),
        "approved", intValue(mapper.countApprovedHumanIntelligence())));
  }

  HumanIntelligenceItem createHumanIntelligence(HumanIntelligenceCreateRequest request, String actor) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("city", blankToDefault(request.city(), "Unknown"));
    values.put("industryId", blankToDefault(request.industryId(), "general"));
    values.put("linkId", blankToDefault(request.linkId(), "general"));
    values.put("content", blankToDefault(request.content(), ""));
    values.put("sourceType", blankToDefault(request.sourceType(), "local_visit"));
    values.put("collector", blankToDefault(request.collector(), actor));
    values.put("eventTime", request.eventTime());
    values.put("confidence", request.confidence() == null ? BigDecimal.valueOf(0.7) : request.confidence());
    values.put("entitlement", blankToDefault(request.entitlement(), "FREE"));
    values.put("regionId", blankToDefault(request.regionId(), "cn-default"));
    values.put("sourceId", blankToDefault(request.sourceId(), "human-intel"));
    mapper.insertHumanIntelligence(values);
    long id = longValue(values.get("id"));
    HumanIntelligenceItem item = findHumanIntelligence(id);
    writeAudit(actor, "ADMIN_HUMAN_INTELLIGENCE_CREATE", "human_intelligence", String.valueOf(id), "SUCCESS", item.regionId(), item.industryId());
    return item;
  }

  HumanIntelligenceItem reviewHumanIntelligence(long id, String verdict, String notes, String actor) {
    String status = "PASS".equals(normalizeVerdict(verdict)) ? "APPROVED" : "REJECTED";
    if (mapper.reviewHumanIntelligence(id, status, actor, notes) == 0) {
      throw new IllegalArgumentException("human intelligence not found");
    }
    HumanIntelligenceItem item = findHumanIntelligence(id);
    if ("APPROVED".equals(status)) {
      mapper.insertHumanIntelligenceAsIntelligence(Map.of(
          "title", "Human intelligence: " + item.city(),
          "content", item.content(),
          "url", "human://" + item.id(),
          "confidence", item.confidence(),
          "linkId", item.linkId(),
          "regionId", item.regionId(),
          "industryId", item.industryId(),
          "sourceId", item.sourceId(),
          "weight", BigDecimal.valueOf(0.9),
          "contentHash", "human-" + item.id()));
    }
    writeAudit(actor, "ADMIN_HUMAN_INTELLIGENCE_" + status, "human_intelligence", String.valueOf(id), "SUCCESS", item.regionId(), item.industryId());
    return item;
  }

  List<RiskRule> listRiskRules() {
    return mapper.listRiskRules().stream().map(this::toRiskRule).toList();
  }

  RiskRule upsertRiskRule(RiskRuleUpsertRequest request, String actor) {
    if (request.id() == null) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("ruleType", blankToDefault(request.ruleType(), "general"));
      values.put("name", blankToDefault(request.name(), "Untitled rule"));
      values.put("description", request.description());
      values.put("enabled", request.enabled() == null || request.enabled());
      values.put("thresholdValue", request.thresholdValue());
      values.put("scopeJson", request.scopeJson());
      values.put("riskLevel", blankToDefault(request.riskLevel(), "MEDIUM"));
      values.put("changeMode", blankToDefault(request.changeMode(), "IMMEDIATE"));
      mapper.insertRiskRule(values);
      long id = longValue(values.get("id"));
      writeAudit(actor, "ADMIN_RISK_RULE_CREATE", "risk_rule", String.valueOf(id), "SUCCESS", "global", "global");
      return findRiskRule(id);
    }

    Map<String, Object> existing = requireMap(mapper.findRiskRule(request.id()), "risk rule not found");
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", request.id());
    values.put("ruleType", blankToDefault(request.ruleType(), stringValue(existing.get("ruleType"))));
    values.put("name", blankToDefault(request.name(), stringValue(existing.get("name"))));
    values.put("description", request.description() == null ? stringValue(existing.get("description")) : request.description());
    values.put("enabled", request.enabled() == null ? booleanValue(existing.get("enabled")) : request.enabled());
    values.put("thresholdValue", request.thresholdValue() == null ? decimalValue(existing.get("thresholdValue"), null) : request.thresholdValue());
    values.put("scopeJson", request.scopeJson() == null ? stringValue(existing.get("scopeJson")) : request.scopeJson());
    values.put("riskLevel", blankToDefault(request.riskLevel(), stringValue(existing.get("riskLevel"))));
    values.put("changeMode", blankToDefault(request.changeMode(), stringValue(existing.get("changeMode"))));
    mapper.updateRiskRule(values);
    writeAudit(actor, "ADMIN_RISK_RULE_UPDATE", "risk_rule", String.valueOf(request.id()), "SUCCESS", "global", "global");
    return findRiskRule(request.id());
  }

  RiskRule toggleRiskRule(long id, String actor) {
    RiskRule rule = findRiskRule(id);
    boolean next = !rule.enabled();
    mapper.toggleRiskRule(id, next);
    writeAudit(actor, next ? "ADMIN_RISK_RULE_ENABLE" : "ADMIN_RISK_RULE_DISABLE", "risk_rule", String.valueOf(id), "SUCCESS", "global", "global");
    return findRiskRule(id);
  }

  void archiveCollectionSource(long sourceConfigId, String actor) {
    if (mapper.findCollectionSourceId(sourceConfigId) == null) {
      throw new IllegalArgumentException("source not found");
    }
    mapper.updateCollectionSourceStatus(sourceConfigId, "ARCHIVED");
    writeAudit(actor, "ADMIN_COLLECTION_ARCHIVE", "collection_source", String.valueOf(sourceConfigId), "SUCCESS", "global", "global");
  }

  void restoreCollectionSource(long sourceConfigId, String actor) {
    if (mapper.findCollectionSourceId(sourceConfigId) == null) {
      throw new IllegalArgumentException("source not found");
    }
    mapper.updateCollectionSourceStatus(sourceConfigId, "ENABLED");
    writeAudit(actor, "ADMIN_COLLECTION_RESTORE", "collection_source", String.valueOf(sourceConfigId), "SUCCESS", "global", "global");
  }

  private AlertItem findAlert(long id) {
    return toAlertItem(requireMap(mapper.findAlert(id), "alert not found"));
  }

  private IntelligenceReviewItem findReview(long id) {
    return toReviewItem(requireMap(mapper.findReview(id), "review not found"));
  }

  private TicketItem findTicket(long id) {
    return toTicketItem(requireMap(mapper.findTicket(id), "ticket not found"));
  }

  private HumanIntelligenceItem findHumanIntelligence(long id) {
    return toHumanItem(requireMap(mapper.findHumanIntelligence(id), "human intelligence not found"));
  }

  private RiskRule findRiskRule(long id) {
    return toRiskRule(requireMap(mapper.findRiskRule(id), "risk rule not found"));
  }

  private void writeAudit(String actor, String action, String targetType, String targetId, String result, String regionId, String industryId) {
    mapper.insertAuditLog(Map.of(
        "actor", actor,
        "action", action,
        "targetType", targetType,
        "targetId", targetId,
        "result", result,
        "regionId", regionId,
        "industryId", industryId));
  }

  private String collectionSuccessRate() {
    int total = intValue(mapper.countCollectionRuns());
    if (total == 0) {
      return "-";
    }
    return Math.round(100.0 * intValue(mapper.countSuccessfulCollectionRuns()) / total) + "%";
  }

  private String crawlerHealth() {
    int openCircuits = intValue(mapper.countOpenCollectionCircuits());
    return openCircuits == 0 ? "All closed" : openCircuits + " open";
  }

  private AlertItem toAlertItem(Map<String, Object> row) {
    return new AlertItem(
        longValue(row, "id"),
        stringValue(row, "level"),
        stringValue(row, "component"),
        stringValue(row, "message"),
        stringValue(row, "status"),
        stringValue(row, "owner"),
        stringValue(row, "regionId"),
        stringValue(row, "industryId"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private AuditLogItem toAuditLogItem(Map<String, Object> row) {
    return new AuditLogItem(
        longValue(row, "id"),
        stringValue(row, "actor"),
        stringValue(row, "action"),
        stringValue(row, "targetType"),
        stringValue(row, "targetId"),
        stringValue(row, "result"),
        stringValue(row, "regionId"),
        stringValue(row, "industryId"),
        timeValue(row, "createTime"));
  }

  private IntelligenceReviewItem toReviewItem(Map<String, Object> row) {
    return new IntelligenceReviewItem(
        longValue(row, "id"),
        longValue(row, "intelligenceId"),
        stringValue(row, "title"),
        stringValue(row, "content"),
        stringValue(row, "url"),
        stringValue(row, "status"),
        stringValue(row, "reviewStatus"),
        stringValue(row, "verdict"),
        stringValue(row, "reviewer"),
        stringValue(row, "reason"),
        decimalValue(lookup(row, "confidence"), BigDecimal.ZERO),
        stringValue(row, "regionId"),
        stringValue(row, "industryId"),
        stringValue(row, "sourceId"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private TicketItem toTicketItem(Map<String, Object> row) {
    return new TicketItem(
        longValue(row, "id"),
        stringValue(row, "ticketType"),
        stringValue(row, "severity"),
        stringValue(row, "targetType"),
        longValue(row, "targetId"),
        stringValue(row, "title"),
        stringValue(row, "status"),
        stringValue(row, "owner"),
        stringValue(row, "nextAction"),
        stringValue(row, "regionId"),
        stringValue(row, "industryId"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private HumanIntelligenceItem toHumanItem(Map<String, Object> row) {
    return new HumanIntelligenceItem(
        longValue(row, "id"),
        stringValue(row, "city"),
        stringValue(row, "industryId"),
        stringValue(row, "linkId"),
        stringValue(row, "content"),
        stringValue(row, "sourceType"),
        stringValue(row, "collector"),
        stringValue(row, "eventTime"),
        decimalValue(lookup(row, "confidence"), BigDecimal.ZERO),
        stringValue(row, "entitlement"),
        stringValue(row, "status"),
        stringValue(row, "reviewer"),
        stringValue(row, "reviewNotes"),
        stringValue(row, "regionId"),
        stringValue(row, "sourceId"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private RiskRule toRiskRule(Map<String, Object> row) {
    return new RiskRule(
        longValue(row, "id"),
        stringValue(row, "ruleType"),
        stringValue(row, "name"),
        stringValue(row, "description"),
        booleanValue(lookup(row, "enabled")),
        decimalValue(lookup(row, "thresholdValue"), null),
        stringValue(row, "scopeJson"),
        stringValue(row, "riskLevel"),
        stringValue(row, "changeMode"),
        intValue(lookup(row, "version")),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private Map<String, Object> requireMap(Map<String, Object> row, String message) {
    if (row == null || row.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return row;
  }

  private Object lookup(Map<String, Object> row, String key) {
    if (row.containsKey(key)) {
      return row.get(key);
    }
    for (Map.Entry<String, Object> entry : row.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private int intValue(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private long longValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private boolean booleanValue(Object value) {
    return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
  }

  private BigDecimal decimalValue(Object value, BigDecimal fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private LocalDateTime timeValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof LocalDateTime time ? time : null;
  }

  private String stringValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return stringValue(value);
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Clob clob) {
      try {
        return clob.getSubString(1, (int) clob.length());
      } catch (SQLException exception) {
        throw new IllegalArgumentException("unable to read clob value", exception);
      }
    }
    return String.valueOf(value);
  }

  private String normalizeVerdict(String verdict) {
    if (!StringUtils.hasText(verdict)) {
      return "FLAG";
    }
    String normalized = verdict.trim().toUpperCase();
    if (List.of("PASS", "REJECT", "FLAG", "SUSPICIOUS", "COMPLIANCE", "PAID_INTEL", "ARCHIVE").contains(normalized)) {
      return normalized;
    }
    return "FLAG";
  }

  private String blankToDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }
}
