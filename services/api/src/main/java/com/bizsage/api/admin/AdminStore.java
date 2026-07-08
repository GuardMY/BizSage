package com.bizsage.api.admin;

import com.bizsage.api.admin.AdminDtos.AdminList;
import com.bizsage.api.admin.AdminDtos.AlertItem;
import com.bizsage.api.admin.AdminDtos.AuditLogItem;
import com.bizsage.api.admin.AdminDtos.Dashboard;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceCreateRequest;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceItem;
import com.bizsage.api.admin.AdminDtos.IntelligenceReviewItem;
import com.bizsage.api.admin.AdminDtos.Metric;
import com.bizsage.api.admin.AdminDtos.TicketItem;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminStore {
  private final JdbcTemplate jdbcTemplate;

  public AdminStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  Dashboard dashboard() {
    List<Metric> metrics = List.of(
        new Metric("pendingReviews", "Pending reviews", String.valueOf(countWhere("admin_intelligence_reviews", "review_status = 'PENDING'")), "warning", "Intelligence waiting for reviewer verdict"),
        new Metric("openTickets", "Open tickets", String.valueOf(countWhere("admin_tickets", "status not in ('CLOSED','ARCHIVED')")), "warning", "Operational ledger items still active"),
        new Metric("openAlerts", "Open alerts", String.valueOf(countWhere("alert_events", "status <> 'CLOSED'")), "critical", "Alerts that still require acknowledgement"),
        new Metric("auditLogs", "Audit logs", String.valueOf(countWhere("audit_logs", "1 = 1")), "healthy", "Recorded administrator operations"),
        new Metric("humanIntel", "Human intelligence", String.valueOf(countWhere("admin_human_intelligence", "1 = 1")), "healthy", "Submitted local intelligence records"));

    return new Dashboard(
        metrics,
        listAlerts("OPEN").items().stream().limit(5).toList(),
        listTickets(null).items().stream().limit(5).toList(),
        listReviews("PENDING").items().stream().limit(5).toList(),
        listAuditLogs(null).items().stream().limit(5).toList());
  }

  AdminList<AlertItem> listAlerts(String status) {
    String where = StringUtils.hasText(status) ? " where status = ?" : "";
    Object[] args = StringUtils.hasText(status) ? new Object[] {status} : new Object[0];
    List<AlertItem> items = jdbcTemplate.query("""
        select id, alert_level, component, message, status, owner, region_id, industry_id, create_time, update_time
          from alert_events
        """ + where + " order by case alert_level when 'P0' then 0 when 'P1' then 1 else 2 end, id desc",
        alertMapper(), args);
    return new AdminList<>(items, items.size(), Map.of(
        "open", countWhere("alert_events", "status <> 'CLOSED'"),
        "p0", countWhere("alert_events", "alert_level = 'P0' and status <> 'CLOSED'")));
  }

  AlertItem updateAlert(long id, String action, String actor, String notes) {
    String status = switch (action) {
      case "acknowledge" -> "ACKNOWLEDGED";
      case "claim" -> "CLAIMED";
      case "close" -> "CLOSED";
      default -> throw new IllegalArgumentException("unsupported alert action");
    };
    int updated = jdbcTemplate.update("""
        update alert_events
           set status = ?, owner = ?, update_time = current_timestamp
         where id = ?
        """, status, actor, id);
    if (updated == 0) {
      throw new IllegalArgumentException("alert not found");
    }
    writeAudit(actor, "ADMIN_ALERT_" + action.toUpperCase(), "alert", String.valueOf(id), "SUCCESS", "global", "global");
    return findAlert(id);
  }

  AdminList<AuditLogItem> listAuditLogs(String query) {
    String like = "%" + (query == null ? "" : query.trim()) + "%";
    List<AuditLogItem> items = jdbcTemplate.query("""
        select id, actor, action, target_type, target_id, result, region_id, industry_id, create_time
          from audit_logs
         where (? = '%%' or actor like ? or action like ? or target_type like ? or target_id like ?)
         order by id desc
        """, auditMapper(), like, like, like, like, like);
    return new AdminList<>(items, items.size(), Map.of("total", items.size()));
  }

  AdminList<IntelligenceReviewItem> listReviews(String status) {
    String where = StringUtils.hasText(status) ? " where r.review_status = ?" : "";
    Object[] args = StringUtils.hasText(status) ? new Object[] {status} : new Object[0];
    List<IntelligenceReviewItem> items = jdbcTemplate.query("""
        select r.id, r.intelligence_id, i.title, i.content, i.url, i.status,
               r.review_status, r.verdict, r.reviewer, r.reason,
               i.confidence, r.region_id, r.industry_id, i.source_id,
               r.create_time, r.update_time
          from admin_intelligence_reviews r
          left join intelligence i on i.id = r.intelligence_id
        """ + where + " order by r.id desc", reviewMapper(), args);
    return new AdminList<>(items, items.size(), Map.of(
        "pending", countWhere("admin_intelligence_reviews", "review_status = 'PENDING'"),
        "completed", countWhere("admin_intelligence_reviews", "review_status = 'COMPLETED'")));
  }

  IntelligenceReviewItem decideReview(long id, String verdict, String notes, String actor) {
    String normalizedVerdict = normalizeVerdict(verdict);
    int updated = jdbcTemplate.update("""
        update admin_intelligence_reviews
           set review_status = 'COMPLETED', verdict = ?, reviewer = ?, reason = ?, update_time = current_timestamp
         where id = ?
        """, normalizedVerdict, actor, notes, id);
    if (updated == 0) {
      throw new IllegalArgumentException("review not found");
    }

    IntelligenceReviewItem item = findReview(id);
    String intelligenceStatus = switch (normalizedVerdict) {
      case "PASS" -> "APPROVED";
      case "REJECT" -> "REJECTED";
      case "FLAG" -> "NEEDS_REVIEW";
      default -> "PENDING";
    };
    jdbcTemplate.update("update intelligence set status = ?, update_time = current_timestamp where id = ?",
        intelligenceStatus, item.intelligenceId());

    if ("FLAG".equals(normalizedVerdict)) {
      jdbcTemplate.update("""
          insert into admin_tickets
            (ticket_type, severity, target_type, target_id, title, description, status, owner, next_action, region_id, industry_id)
          values ('review_escalation', 'P1', 'intelligence', ?, ?, ?, 'NEW', ?, 'Assign second reviewer and confirm source conflict.', ?, ?)
          """, item.intelligenceId(), "Escalated intelligence review: " + item.title(), notes, actor, item.regionId(), item.industryId());
    }

    writeAudit(actor, "ADMIN_INTELLIGENCE_" + normalizedVerdict, "intelligence", String.valueOf(item.intelligenceId()), "SUCCESS", item.regionId(), item.industryId());
    return findReview(id);
  }

  AdminList<TicketItem> listTickets(String status) {
    String where = StringUtils.hasText(status) ? " where status = ?" : "";
    Object[] args = StringUtils.hasText(status) ? new Object[] {status} : new Object[0];
    List<TicketItem> items = jdbcTemplate.query("""
        select id, ticket_type, severity, target_type, target_id, title, status, owner,
               next_action, region_id, industry_id, create_time, update_time
          from admin_tickets
        """ + where + " order by case severity when 'P0' then 0 when 'P1' then 1 else 2 end, id desc",
        ticketMapper(), args);
    return new AdminList<>(items, items.size(), Map.of(
        "open", countWhere("admin_tickets", "status not in ('CLOSED','ARCHIVED')"),
        "p0", countWhere("admin_tickets", "severity = 'P0' and status not in ('CLOSED','ARCHIVED')")));
  }

  TicketItem transitionTicket(long id, String status, String owner, String nextAction, String actor, String notes) {
    int updated = jdbcTemplate.update("""
        update admin_tickets
           set status = ?, owner = ?, next_action = ?, update_time = current_timestamp
         where id = ?
        """, blankToDefault(status, "IN_PROGRESS"), blankToDefault(owner, actor), blankToDefault(nextAction, "Continue handling"), id);
    if (updated == 0) {
      throw new IllegalArgumentException("ticket not found");
    }
    TicketItem item = findTicket(id);
    writeAudit(actor, "ADMIN_TICKET_TRANSITION", "ticket", String.valueOf(id), "SUCCESS", item.regionId(), item.industryId());
    return item;
  }

  AdminList<HumanIntelligenceItem> listHumanIntelligence(String status) {
    String where = StringUtils.hasText(status) ? " where status = ?" : "";
    Object[] args = StringUtils.hasText(status) ? new Object[] {status} : new Object[0];
    List<HumanIntelligenceItem> items = jdbcTemplate.query("""
        select id, city, industry_id, link_id, content, source_type, collector, event_time,
               confidence, entitlement, status, reviewer, review_notes, region_id, source_id, create_time, update_time
          from admin_human_intelligence
        """ + where + " order by id desc", humanMapper(), args);
    return new AdminList<>(items, items.size(), Map.of(
        "pending", countWhere("admin_human_intelligence", "status = 'PENDING_REVIEW'"),
        "approved", countWhere("admin_human_intelligence", "status = 'APPROVED'")));
  }

  HumanIntelligenceItem createHumanIntelligence(HumanIntelligenceCreateRequest request, String actor) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_human_intelligence
            (city, industry_id, link_id, content, source_type, collector, event_time,
             confidence, entitlement, status, region_id, source_id)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, blankToDefault(request.city(), "Unknown"));
      ps.setString(2, blankToDefault(request.industryId(), "general"));
      ps.setString(3, blankToDefault(request.linkId(), "general"));
      ps.setString(4, blankToDefault(request.content(), ""));
      ps.setString(5, blankToDefault(request.sourceType(), "local_visit"));
      ps.setString(6, blankToDefault(request.collector(), actor));
      ps.setString(7, request.eventTime());
      ps.setBigDecimal(8, request.confidence() == null ? BigDecimal.valueOf(0.7) : request.confidence());
      ps.setString(9, blankToDefault(request.entitlement(), "FREE"));
      ps.setString(10, blankToDefault(request.regionId(), "cn-default"));
      ps.setString(11, blankToDefault(request.sourceId(), "human-intel"));
      return ps;
    }, keyHolder);
    long id = generatedId(keyHolder);
    HumanIntelligenceItem item = findHumanIntelligence(id);
    writeAudit(actor, "ADMIN_HUMAN_INTELLIGENCE_CREATE", "human_intelligence", String.valueOf(id), "SUCCESS", item.regionId(), item.industryId());
    return item;
  }

  HumanIntelligenceItem reviewHumanIntelligence(long id, String verdict, String notes, String actor) {
    String status = "PASS".equals(normalizeVerdict(verdict)) ? "APPROVED" : "REJECTED";
    int updated = jdbcTemplate.update("""
        update admin_human_intelligence
           set status = ?, reviewer = ?, review_notes = ?, update_time = current_timestamp
         where id = ?
        """, status, actor, notes, id);
    if (updated == 0) {
      throw new IllegalArgumentException("human intelligence not found");
    }
    HumanIntelligenceItem item = findHumanIntelligence(id);
    if ("APPROVED".equals(status)) {
      jdbcTemplate.update("""
          insert into intelligence
            (title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, content_hash)
          values (?, ?, ?, 'APPROVED', ?, ?, ?, ?, ?, ?, ?)
          """,
          "Human intelligence: " + item.city(),
          item.content(),
          "human://" + item.id(),
          item.confidence(),
          item.linkId(),
          item.regionId(),
          item.industryId(),
          item.sourceId(),
          BigDecimal.valueOf(0.9),
          "human-" + item.id());
    }
    writeAudit(actor, "ADMIN_HUMAN_INTELLIGENCE_" + status, "human_intelligence", String.valueOf(id), "SUCCESS", item.regionId(), item.industryId());
    return item;
  }

  private AlertItem findAlert(long id) {
    return jdbcTemplate.query("select id, alert_level, component, message, status, owner, region_id, industry_id, create_time, update_time from alert_events where id = ?",
        alertMapper(), id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("alert not found"));
  }

  private IntelligenceReviewItem findReview(long id) {
    return jdbcTemplate.query("""
        select r.id, r.intelligence_id, i.title, i.content, i.url, i.status,
               r.review_status, r.verdict, r.reviewer, r.reason,
               i.confidence, r.region_id, r.industry_id, i.source_id,
               r.create_time, r.update_time
          from admin_intelligence_reviews r
          left join intelligence i on i.id = r.intelligence_id
         where r.id = ?
        """, reviewMapper(), id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("review not found"));
  }

  private TicketItem findTicket(long id) {
    return jdbcTemplate.query("""
        select id, ticket_type, severity, target_type, target_id, title, status, owner,
               next_action, region_id, industry_id, create_time, update_time
          from admin_tickets
         where id = ?
        """, ticketMapper(), id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("ticket not found"));
  }

  private HumanIntelligenceItem findHumanIntelligence(long id) {
    return jdbcTemplate.query("""
        select id, city, industry_id, link_id, content, source_type, collector, event_time,
               confidence, entitlement, status, reviewer, review_notes, region_id, source_id, create_time, update_time
          from admin_human_intelligence
         where id = ?
        """, humanMapper(), id).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("human intelligence not found"));
  }

  private void writeAudit(String actor, String action, String targetType, String targetId, String result, String regionId, String industryId) {
    jdbcTemplate.update("""
        insert into audit_logs (actor, action, target_type, target_id, result, region_id, industry_id)
        values (?, ?, ?, ?, ?, ?, ?)
        """, actor, action, targetType, targetId, result, regionId, industryId);
  }

  private int countWhere(String table, String where) {
    Integer count = jdbcTemplate.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
    return count == null ? 0 : count;
  }

  private RowMapper<AlertItem> alertMapper() {
    return (rs, rowNum) -> new AlertItem(
        rs.getLong("id"),
        rs.getString("alert_level"),
        rs.getString("component"),
        rs.getString("message"),
        rs.getString("status"),
        rs.getString("owner"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<AuditLogItem> auditMapper() {
    return (rs, rowNum) -> new AuditLogItem(
        rs.getLong("id"),
        rs.getString("actor"),
        rs.getString("action"),
        rs.getString("target_type"),
        rs.getString("target_id"),
        rs.getString("result"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        timestamp(rs.getTimestamp("create_time")));
  }

  private RowMapper<IntelligenceReviewItem> reviewMapper() {
    return (rs, rowNum) -> new IntelligenceReviewItem(
        rs.getLong("id"),
        rs.getLong("intelligence_id"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("url"),
        rs.getString("status"),
        rs.getString("review_status"),
        rs.getString("verdict"),
        rs.getString("reviewer"),
        rs.getString("reason"),
        rs.getBigDecimal("confidence"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("source_id"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<TicketItem> ticketMapper() {
    return (rs, rowNum) -> new TicketItem(
        rs.getLong("id"),
        rs.getString("ticket_type"),
        rs.getString("severity"),
        rs.getString("target_type"),
        rs.getLong("target_id"),
        rs.getString("title"),
        rs.getString("status"),
        rs.getString("owner"),
        rs.getString("next_action"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<HumanIntelligenceItem> humanMapper() {
    return (rs, rowNum) -> new HumanIntelligenceItem(
        rs.getLong("id"),
        rs.getString("city"),
        rs.getString("industry_id"),
        rs.getString("link_id"),
        rs.getString("content"),
        rs.getString("source_type"),
        rs.getString("collector"),
        rs.getString("event_time"),
        rs.getBigDecimal("confidence"),
        rs.getString("entitlement"),
        rs.getString("status"),
        rs.getString("reviewer"),
        rs.getString("review_notes"),
        rs.getString("region_id"),
        rs.getString("source_id"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private LocalDateTime timestamp(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }

  private String normalizeVerdict(String verdict) {
    if (!StringUtils.hasText(verdict)) {
      return "FLAG";
    }
    String normalized = verdict.trim().toUpperCase();
    if (List.of("PASS", "REJECT", "FLAG").contains(normalized)) {
      return normalized;
    }
    return "FLAG";
  }

  private String blankToDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
