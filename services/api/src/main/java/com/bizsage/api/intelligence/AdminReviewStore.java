package com.bizsage.api.intelligence;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/**
 * Manages review tickets in the admin_intelligence_reviews table.
 *
 * <p>Review tickets are created automatically when intelligence items
 * are ingested via the collection pipeline.  Operators review and
 * approve/reject them through the admin UI.
 */
@Service
public class AdminReviewStore {

  private static final Logger log = LoggerFactory.getLogger(AdminReviewStore.class);

  private final JdbcTemplate jdbcTemplate;

  public AdminReviewStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Create a review ticket for a newly-collected intelligence item.
   * The ticket starts in PENDING status and appears in the operator's
   * review queue.
   */
  public ReviewTicket createTicket(long intelligenceId, IntelligenceItem item) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_intelligence_reviews
            (intelligence_id, review_status, source_id, weight,
             region_id, industry_id, evidence_json)
          values (?, 'PENDING', 'auto-collection', 1.0000, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, intelligenceId);
      ps.setString(2, item.regionId() != null ? item.regionId() : "global");
      ps.setString(3, item.industryId() != null ? item.industryId() : "global");
      ps.setString(4, evidenceJson(item));
      return ps;
    }, keyHolder);

    long ticketId = generatedId(keyHolder);
    log.debug("Created review ticket {} for intelligence {}", ticketId, intelligenceId);
    return findTicket(ticketId);
  }

  /**
   * Create review tickets for a batch of newly-created intelligence items.
   *
   * @return the number of tickets created
   */
  public int createTickets(List<IntelligenceItem> items) {
    int count = 0;
    for (IntelligenceItem item : items) {
      try {
        createTicket(item.id(), item);
        count++;
      } catch (Exception ex) {
        log.warn("Failed to create review ticket for intelligence {}: {}",
            item.id(), ex.getMessage());
      }
    }
    return count;
  }

  /** List all pending review tickets. */
  public List<ReviewTicket> listPending() {
    return jdbcTemplate.query(
        baseSelect() + " where review_status = 'PENDING' order by id", mapper());
  }

  /** List review tickets for a specific intelligence item. */
  public List<ReviewTicket> listForIntelligence(long intelligenceId) {
    return jdbcTemplate.query(
        baseSelect() + " where intelligence_id = ? order by id", mapper(), intelligenceId);
  }

  private ReviewTicket findTicket(long ticketId) {
    return jdbcTemplate.query(
        baseSelect() + " where id = ?", mapper(), ticketId).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("review ticket not found"));
  }

  private String baseSelect() {
    return """
        select id, intelligence_id, review_status, verdict, reviewer,
               reason, source_id, weight, region_id, industry_id, create_time, update_time
          from admin_intelligence_reviews
        """;
  }

  private String evidenceJson(IntelligenceItem item) {
    return "{\"title\":\"" + escapeJson(item.title())
        + "\",\"source_url\":\"" + escapeJson(item.url() != null ? item.url() : "")
        + "\",\"source_id\":\"" + escapeJson(item.sourceId() != null ? item.sourceId() : "collector")
        + "\",\"confidence\":" + item.confidence()
        + ",\"weight\":" + item.weight() + "}";
  }

  private static String escapeJson(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private RowMapper<ReviewTicket> mapper() {
    return (rs, rowNum) -> new ReviewTicket(
        rs.getLong("id"),
        rs.getLong("intelligence_id"),
        rs.getString("review_status"),
        rs.getString("verdict"),
        rs.getString("reviewer"),
        rs.getString("reason"),
        rs.getString("source_id"),
        rs.getDouble("weight"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private LocalDateTime timestamp(Timestamp ts) {
    return ts == null ? null : ts.toLocalDateTime();
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
