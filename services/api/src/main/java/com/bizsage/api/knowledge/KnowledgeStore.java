package com.bizsage.api.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class KnowledgeStore {
  private final JdbcTemplate jdbcTemplate;

  public KnowledgeStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public KnowledgeItem importItem(ImportKnowledgeRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into knowledge_items
            (title, content, confidence, link_id, region_id, industry_id, source_id, weight)
          values (?, ?, 0.85, ?, ?, ?, ?, 0.85)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, request.title());
      ps.setString(2, request.content());
      ps.setString(3, request.linkId());
      ps.setString(4, request.regionId());
      ps.setString(5, request.industryId());
      ps.setString(6, request.sourceId());
      return ps;
    }, keyHolder);
    return find(generatedId(keyHolder));
  }

  public List<KnowledgeItem> list() {
    return jdbcTemplate.query(baseSelect() + " order by id", mapper());
  }

  /**
   * Returns knowledge items scoped to the given region and industry.
   * When regionId or industryId is null (admin scope), no filter is applied
   * for that dimension.
   */
  public List<KnowledgeItem> listScoped(String regionId, String industryId) {
    StringBuilder sql = new StringBuilder(baseSelect()).append(" where 1=1");
    List<Object> params = new ArrayList<>();
    if (regionId != null) {
      sql.append(" and region_id = ?");
      params.add(regionId);
    }
    if (industryId != null) {
      sql.append(" and industry_id = ?");
      params.add(industryId);
    }
    sql.append(" order by id");
    return jdbcTemplate.query(sql.toString(), mapper(), params.toArray());
  }

  private KnowledgeItem find(long id) {
    return jdbcTemplate.query(baseSelect() + " where id = ?", mapper(), id).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("knowledge item not found"));
  }

  private String baseSelect() {
    return """
        select id, title, content, industry_id, region_id, link_id, source_id,
               coalesce(source_url, '') as source_url, confidence, weight
          from knowledge_items
        """;
  }

  private RowMapper<KnowledgeItem> mapper() {
    return (rs, rowNum) -> new KnowledgeItem(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getString("industry_id"),
        rs.getString("region_id"),
        rs.getString("link_id"),
        rs.getString("source_id"),
        rs.getString("source_url"),
        rs.getDouble("confidence"),
        rs.getDouble("weight"));
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
