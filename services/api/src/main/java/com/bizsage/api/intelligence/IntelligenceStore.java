package com.bizsage.api.intelligence;

import com.bizsage.api.governance.DataScope;
import com.bizsage.api.worker.AiWorkerClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceStore {
  private static final Logger log = LoggerFactory.getLogger(IntelligenceStore.class);

  private final JdbcTemplate jdbcTemplate;
  private final AiWorkerClient aiWorkerClient;

  public IntelligenceStore(JdbcTemplate jdbcTemplate, AiWorkerClient aiWorkerClient) {
    this.jdbcTemplate = jdbcTemplate;
    this.aiWorkerClient = aiWorkerClient;
  }

  public IntelligenceItem create(CreateIntelligenceRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into intelligence
            (title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, content_hash)
          values (?, ?, ?, 'PENDING', 0.6, ?, ?, ?, ?, 0.6, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, request.title());
      ps.setString(2, request.content());
      ps.setString(3, request.url());
      ps.setString(4, request.linkId());
      ps.setString(5, request.regionId());
      ps.setString(6, request.industryId());
      ps.setString(7, request.sourceId());
      ps.setString(8, sha256(request.content()));
      return ps;
    }, keyHolder);
    return find(generatedId(keyHolder));
  }

  /**
   * Create an intelligence item from a governed collector record.
   * The item is created with status='PENDING' for operator review.
   */
  public IntelligenceItem createFromRecord(Map<String, Object> record) {
    String content = stringField(record, "content", "");
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into intelligence
            (title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, content_hash)
          values (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, stringField(record, "title", "Untitled"));
      ps.setString(2, content);
      ps.setString(3, stringField(record, "url", null));
      ps.setDouble(4, doubleField(record, "confidence", 0.6));
      ps.setString(5, stringField(record, "link_id", "collection"));
      ps.setString(6, stringField(record, "region_id", "cn-default"));
      ps.setString(7, stringField(record, "industry_id", "general"));
      ps.setString(8, stringField(record, "source_id", "collector"));
      ps.setDouble(9, doubleField(record, "weight", 0.6));
      ps.setString(10, sha256(content));
      return ps;
    }, keyHolder);
    return find(generatedId(keyHolder));
  }

  /**
   * Bulk-create intelligence items from governed collector records.
   * All items are created with status='PENDING'.
   *
   * @return the list of created intelligence items
   */
  public List<IntelligenceItem> bulkCreateFromRecords(List<Map<String, Object>> records) {
    List<IntelligenceItem> created = new ArrayList<>();
    for (Map<String, Object> record : records) {
      try {
        created.add(createFromRecord(record));
      } catch (Exception ex) {
        log.warn("Failed to create intelligence from record: {}", ex.getMessage());
      }
    }
    return created;
  }

  public IntelligenceItem approve(long id) {
    int updated = jdbcTemplate.update(
        "update intelligence set status = 'APPROVED', update_time = current_timestamp where id = ?", id);
    if (updated == 0) {
      throw new IllegalArgumentException("intelligence not found");
    }
    IntelligenceItem item = find(id);
    syncToQdrant(item);
    return item;
  }

  public List<IntelligenceItem> list() {
    return jdbcTemplate.query("""
        select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight
          from intelligence
         order by id
        """, mapper());
  }

  /**
   * Returns intelligence scoped to the given DataScope for data isolation enforcement.
   * Admins (null region/industry) see all records; other users only see their own scope.
   */
  public List<IntelligenceItem> listScoped(DataScope scope) {
    if (scope.isAdmin()) {
      return list();
    }
    StringBuilder sql = new StringBuilder("""
        select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight
          from intelligence where 1=1
        """);
    List<Object> params = new ArrayList<>();

    if (scope.regionId() != null) {
      sql.append(" and region_id = ?");
      params.add(scope.regionId());
    }
    if (scope.industryId() != null) {
      sql.append(" and industry_id = ?");
      params.add(scope.industryId());
    }
    if (!scope.canAccessPaid()) {
      sql.append(" and (url not like '%paid%' or url is null)");
    }
    sql.append(" order by id");

    return jdbcTemplate.query(sql.toString(), mapper(), params.toArray());
  }

  /**
   * Returns approved intelligence scoped to the given DataScope for use in diagnosis.
   * Only items with status='APPROVED' are included.
   */
  public List<IntelligenceItem> listApprovedScoped(DataScope scope) {
    if (scope.isAdmin()) {
      return jdbcTemplate.query("""
          select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight
            from intelligence
           where status = 'APPROVED'
           order by id
          """, mapper());
    }
    StringBuilder sql = new StringBuilder("""
        select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight
          from intelligence where status = 'APPROVED'
        """);
    List<Object> params = new ArrayList<>();

    if (scope.regionId() != null) {
      sql.append(" and region_id = ?");
      params.add(scope.regionId());
    }
    if (scope.industryId() != null) {
      sql.append(" and industry_id = ?");
      params.add(scope.industryId());
    }
    if (!scope.canAccessPaid()) {
      sql.append(" and (url not like '%paid%' or url is null)");
    }
    sql.append(" order by id");

    return jdbcTemplate.query(sql.toString(), mapper(), params.toArray());
  }

  private IntelligenceItem find(long id) {
    return jdbcTemplate.query("""
        select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight
          from intelligence
         where id = ?
        """, mapper(), id).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("intelligence not found"));
  }

  private RowMapper<IntelligenceItem> mapper() {
    return (rs, rowNum) -> new IntelligenceItem(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("url"),
        rs.getString("status"),
        rs.getDouble("confidence"),
        rs.getString("link_id"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("source_id"),
        rs.getDouble("weight"));
  }

  /**
   * Sync an approved intelligence item to the AI worker's Qdrant vector store.
   * Non-fatal — failures are logged but do not block the approval.
   */
  private void syncToQdrant(IntelligenceItem item) {
    try {
      Map<String, Object> knowledgeItem = new LinkedHashMap<>();
      knowledgeItem.put("id", "intel-" + item.id());
      knowledgeItem.put("title", item.title());
      knowledgeItem.put("content", item.content());
      knowledgeItem.put("source_url", item.url() != null ? item.url() : "");
      knowledgeItem.put("source_id", item.sourceId() != null ? item.sourceId() : "intelligence");
      knowledgeItem.put("weight", item.weight());
      knowledgeItem.put("confidence", item.confidence());
      knowledgeItem.put("industry_id", item.industryId() != null ? item.industryId() : "general");
      knowledgeItem.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
      knowledgeItem.put("entitlement", "FREE");
      aiWorkerClient.syncKnowledge(List.of(knowledgeItem));
    } catch (Exception ex) {
      log.warn("Failed to sync approved intelligence {} to Qdrant (non-fatal): {}",
          item.id(), ex.getMessage());
    }
  }

  private static String stringField(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value instanceof String str ? str : defaultValue;
  }

  private static double doubleField(Map<String, Object> map, String key, double defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number num) {
      return num.doubleValue();
    }
    return defaultValue;
  }

  static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
