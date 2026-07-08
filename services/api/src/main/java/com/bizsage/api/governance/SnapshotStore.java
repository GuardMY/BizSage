package com.bizsage.api.governance;

import com.bizsage.api.governance.SnapshotDtos.SnapshotCompareResult;
import com.bizsage.api.governance.SnapshotDtos.SnapshotDetail;
import com.bizsage.api.governance.SnapshotDtos.SnapshotSummary;
import com.bizsage.api.governance.SnapshotDtos.SnapshotType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class SnapshotStore {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SnapshotStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public SnapshotDetail create(SnapshotType type, String scopeKey, String payloadJson,
      String regionId, String industryId, int retentionDays, int recordCount) {
    Long parentId = findLatestByTypeAndScope(type, regionId, industryId)
        .map(SnapshotSummary::id)
        .orElse(null);

    LocalDateTime expiresAt = LocalDateTime.now().plusDays(retentionDays);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into intelligence_snapshots
            (snapshot_type, scope_key, payload_json, source_id, weight, region_id, industry_id,
             retention_days, record_count, parent_snapshot_id, expires_at)
          values (?, ?, ?, 'snapshot', 1.0000, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, type.name());
      ps.setString(2, scopeKey);
      ps.setString(3, payloadJson);
      ps.setString(4, regionId);
      ps.setString(5, industryId);
      ps.setInt(6, retentionDays);
      ps.setInt(7, recordCount);
      if (parentId != null) {
        ps.setLong(8, parentId);
      } else {
        ps.setNull(8, java.sql.Types.BIGINT);
      }
      ps.setTimestamp(9, Timestamp.valueOf(expiresAt));
      return ps;
    }, keyHolder);
    return find(generatedId(keyHolder));
  }

  public SnapshotDetail find(long id) {
    return jdbcTemplate.query(
        "select id, snapshot_type, scope_key, payload_json, region_id, industry_id, "
            + "retention_days, record_count, parent_snapshot_id, expires_at, create_time "
            + "from intelligence_snapshots where id = ?",
        detailMapper(), id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("snapshot not found: " + id));
  }

  public List<SnapshotSummary> listByScope(SnapshotType type, String regionId, String industryId, int limit) {
    return jdbcTemplate.query(
        "select id, snapshot_type, scope_key, region_id, industry_id, retention_days, "
            + "record_count, expires_at, create_time "
            + "from intelligence_snapshots "
            + "where snapshot_type = ? and region_id = ? and industry_id = ? "
            + "order by create_time desc limit ?",
        summaryMapper(), type.name(), regionId, industryId, limit);
  }

  public Optional<SnapshotSummary> findLatestByTypeAndScope(SnapshotType type, String regionId, String industryId) {
    List<SnapshotSummary> results = jdbcTemplate.query(
        "select id, snapshot_type, scope_key, region_id, industry_id, retention_days, "
            + "record_count, expires_at, create_time "
            + "from intelligence_snapshots "
            + "where snapshot_type = ? and region_id = ? and industry_id = ? "
            + "order by create_time desc limit 1",
        summaryMapper(), type.name(), regionId, industryId);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  public SnapshotCompareResult compare(long leftId, long rightId) {
    SnapshotDetail left = find(leftId);
    SnapshotDetail right = find(rightId);

    List<Map<String, Object>> leftRecords = parsePayload(left.payloadJson());
    List<Map<String, Object>> rightRecords = parsePayload(right.payloadJson());

    Set<String> leftKeys = extractKeys(leftRecords);
    Set<String> rightKeys = extractKeys(rightRecords);

    List<String> added = new ArrayList<>(rightKeys);
    added.removeAll(leftKeys);

    List<String> removed = new ArrayList<>(leftKeys);
    removed.removeAll(rightKeys);

    Set<String> common = new HashSet<>(leftKeys);
    common.retainAll(rightKeys);
    List<String> changed = new ArrayList<>();
    for (String key : common) {
      Map<String, Object> lr = findByKey(leftRecords, key);
      Map<String, Object> rr = findByKey(rightRecords, key);
      if (lr != null && rr != null) {
        String lc = String.valueOf(lr.getOrDefault("confidence", ""));
        String rc = String.valueOf(rr.getOrDefault("confidence", ""));
        String ls = String.valueOf(lr.getOrDefault("status", ""));
        String rs = String.valueOf(rr.getOrDefault("status", ""));
        if (!lc.equals(rc) || !ls.equals(rs)) {
          changed.add(key);
        }
      }
    }

    return new SnapshotCompareResult(leftId, rightId, added, removed, changed);
  }

  public int deleteExpired() {
    return jdbcTemplate.update(
        "delete from intelligence_snapshots where expires_at is not null and expires_at <= ?",
        Timestamp.valueOf(LocalDateTime.now()));
  }

  public List<String> distinctScopes() {
    return jdbcTemplate.query(
        "select distinct concat(region_id, '|', industry_id) from intelligence where status = 'APPROVED'",
        (rs, rowNum) -> rs.getString(1));
  }

  private String extractKey(Map<String, Object> record) {
    return String.format("%s|%s|%s",
        record.getOrDefault("title", ""),
        record.getOrDefault("link_id", ""),
        record.getOrDefault("source_id", ""));
  }

  private Set<String> extractKeys(List<Map<String, Object>> records) {
    Set<String> keys = new HashSet<>();
    for (Map<String, Object> r : records) {
      keys.add(extractKey(r));
    }
    return keys;
  }

  private Map<String, Object> findByKey(List<Map<String, Object>> records, String key) {
    return records.stream().filter(r -> extractKey(r).equals(key)).findFirst().orElse(null);
  }

  private List<Map<String, Object>> parsePayload(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private RowMapper<SnapshotSummary> summaryMapper() {
    return (rs, rowNum) -> new SnapshotSummary(
        rs.getLong("id"),
        rs.getString("snapshot_type"),
        rs.getString("scope_key"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getInt("retention_days"),
        rs.getInt("record_count"),
        toLocalDateTime(rs.getTimestamp("expires_at")),
        toLocalDateTime(rs.getTimestamp("create_time")));
  }

  private RowMapper<SnapshotDetail> detailMapper() {
    return (rs, rowNum) -> new SnapshotDetail(
        rs.getLong("id"),
        rs.getString("snapshot_type"),
        rs.getString("scope_key"),
        rs.getString("payload_json"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getInt("retention_days"),
        rs.getInt("record_count"),
        (Long) rs.getObject("parent_snapshot_id"),
        toLocalDateTime(rs.getTimestamp("expires_at")),
        toLocalDateTime(rs.getTimestamp("create_time")));
  }

  private LocalDateTime toLocalDateTime(Timestamp ts) {
    return ts == null ? null : ts.toLocalDateTime();
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
