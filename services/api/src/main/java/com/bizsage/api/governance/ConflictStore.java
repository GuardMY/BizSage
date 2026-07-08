package com.bizsage.api.governance;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

/**
 * Persistence for the seven-layer conflict engine results.
 * Stores conflict resolution records and manages the false-information ledger.
 */
@Service
public class ConflictStore {

  private final JdbcTemplate jdbcTemplate;

  public ConflictStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public record ConflictResolutionRecord(
      long id,
      Long incomingIntelligenceId,
      Long existingIntelligenceId,
      String conflictBranch,
      int simHashDistance,
      double incomingWeight,
      double existingWeight,
      String routingAction,
      Long reviewTicketId,
      String resolvedBy,
      String notes,
      String regionId,
      String industryId,
      String linkId,
      String createTime) {}

  public record FalseLedgerItem(
      long id,
      Long originalIntelligenceId,
      String title,
      String contentHash,
      String conflictReason,
      String matchedRumorKeyword,
      String regionId,
      String industryId,
      String archivedBy,
      String createTime) {}

  /**
   * Persist a conflict resolution decision made by the Python engine or an operator.
   */
  public ConflictResolutionRecord recordConflict(
      Long incomingIntelId,
      Long existingIntelId,
      String conflictBranch,
      int simHashDistance,
      double incomingWeight,
      double existingWeight,
      String routingAction,
      Long reviewTicketId,
      String resolvedBy,
      String notes,
      String regionId,
      String industryId,
      String linkId) {

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into conflict_resolutions
            (incoming_intelligence_id, existing_intelligence_id, conflict_branch,
             sim_hash_distance, incoming_weight, existing_weight, routing_action,
             review_ticket_id, resolved_by, notes, region_id, industry_id, link_id)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setObject(1, incomingIntelId);
      ps.setObject(2, existingIntelId);
      ps.setString(3, conflictBranch);
      ps.setInt(4, simHashDistance);
      ps.setDouble(5, incomingWeight);
      ps.setDouble(6, existingWeight);
      ps.setString(7, routingAction);
      ps.setObject(8, reviewTicketId);
      ps.setString(9, resolvedBy);
      ps.setString(10, notes);
      ps.setString(11, regionId);
      ps.setString(12, industryId);
      ps.setString(13, linkId);
      return ps;
    }, keyHolder);

    long id = generatedId(keyHolder);
    return findResolution(id);
  }

  /**
   * Archive a record to the false-information ledger.
   */
  public FalseLedgerItem archiveAsFalse(
      Long intelligenceId, String title, String content, String contentHash,
      String reason, String matchedKeyword, String regionId, String industryId,
      String linkId, String sourceId, String archivedBy, String archiveReason) {

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into false_information_ledger
            (original_intelligence_id, title, content, content_hash, conflict_reason,
             matched_rumor_keyword, source_id, region_id, industry_id, link_id,
             archived_by, archive_reason)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setObject(1, intelligenceId);
      ps.setString(2, title);
      ps.setString(3, content);
      ps.setString(4, contentHash);
      ps.setString(5, reason);
      ps.setString(6, matchedKeyword);
      ps.setString(7, sourceId);
      ps.setString(8, regionId);
      ps.setString(9, industryId);
      ps.setString(10, linkId);
      ps.setString(11, archivedBy);
      ps.setString(12, archiveReason);
      return ps;
    }, keyHolder);

    return findFalseLedgerItem(generatedId(keyHolder));
  }

  public List<ConflictResolutionRecord> listConflicts(String regionId, String industryId, int limit) {
    return jdbcTemplate.query(
        "select id, incoming_intelligence_id, existing_intelligence_id, conflict_branch, "
            + "sim_hash_distance, incoming_weight, existing_weight, routing_action, "
            + "review_ticket_id, resolved_by, notes, region_id, industry_id, link_id, create_time "
            + "from conflict_resolutions "
            + "where region_id = ? and industry_id = ? "
            + "order by create_time desc limit ?",
        conflictMapper(), regionId, industryId, limit);
  }

  public List<FalseLedgerItem> listFalseLedger(String regionId, String industryId, int limit) {
    return jdbcTemplate.query(
        "select id, original_intelligence_id, title, content_hash, conflict_reason, "
            + "matched_rumor_keyword, region_id, industry_id, archived_by, create_time "
            + "from false_information_ledger "
            + "where region_id = ? and industry_id = ? "
            + "order by create_time desc limit ?",
        falseLedgerMapper(), regionId, industryId, limit);
  }

  public boolean isContentInFalseLedger(String contentHash) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from false_information_ledger where content_hash = ?",
        Integer.class, contentHash);
    return count != null && count > 0;
  }

  private ConflictResolutionRecord findResolution(long id) {
    return jdbcTemplate.query(
        "select id, incoming_intelligence_id, existing_intelligence_id, conflict_branch, "
            + "sim_hash_distance, incoming_weight, existing_weight, routing_action, "
            + "review_ticket_id, resolved_by, notes, region_id, industry_id, link_id, create_time "
            + "from conflict_resolutions where id = ?",
        conflictMapper(), id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("conflict resolution not found: " + id));
  }

  private FalseLedgerItem findFalseLedgerItem(long id) {
    return jdbcTemplate.query(
        "select id, original_intelligence_id, title, content_hash, conflict_reason, "
            + "matched_rumor_keyword, region_id, industry_id, archived_by, create_time "
            + "from false_information_ledger where id = ?",
        falseLedgerMapper(), id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("false ledger item not found: " + id));
  }

  private RowMapper<ConflictResolutionRecord> conflictMapper() {
    return (rs, rowNum) -> new ConflictResolutionRecord(
        rs.getLong("id"),
        (Long) rs.getObject("incoming_intelligence_id"),
        (Long) rs.getObject("existing_intelligence_id"),
        rs.getString("conflict_branch"),
        rs.getInt("sim_hash_distance"),
        rs.getDouble("incoming_weight"),
        rs.getDouble("existing_weight"),
        rs.getString("routing_action"),
        (Long) rs.getObject("review_ticket_id"),
        rs.getString("resolved_by"),
        rs.getString("notes"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("link_id"),
        rs.getString("create_time"));
  }

  private RowMapper<FalseLedgerItem> falseLedgerMapper() {
    return (rs, rowNum) -> new FalseLedgerItem(
        rs.getLong("id"),
        (Long) rs.getObject("original_intelligence_id"),
        rs.getString("title"),
        rs.getString("content_hash"),
        rs.getString("conflict_reason"),
        rs.getString("matched_rumor_keyword"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("archived_by"),
        rs.getString("create_time"));
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
