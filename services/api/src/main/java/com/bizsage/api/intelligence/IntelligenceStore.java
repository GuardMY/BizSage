package com.bizsage.api.intelligence;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HexFormat;

@Service
public class IntelligenceStore {
  private final JdbcTemplate jdbcTemplate;

  public IntelligenceStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
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

  public IntelligenceItem approve(long id) {
    int updated = jdbcTemplate.update("update intelligence set status = 'APPROVED', update_time = current_timestamp where id = ?", id);
    if (updated == 0) {
      throw new IllegalArgumentException("intelligence not found");
    }
    return find(id);
  }

  public List<IntelligenceItem> list() {
    return jdbcTemplate.query("""
        select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight
          from intelligence
         order by id
        """, mapper());
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
