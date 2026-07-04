package com.bizsage.api.intelligence;

import com.bizsage.api.users.UserAccount;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class PaidIntelligenceStore {
  private final JdbcTemplate jdbcTemplate;

  public PaidIntelligenceStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public PaidIntelligenceItem create(CreateIntelligenceRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into paid_intelligence
            (title, content, url, status, confidence, entitlement, link_id, region_id, industry_id, source_id, weight, content_hash)
          values (?, ?, ?, 'PENDING', 0.75, 'PAID', ?, ?, ?, ?, 0.9, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, request.title());
      ps.setString(2, request.content());
      ps.setString(3, request.url());
      ps.setString(4, request.linkId());
      ps.setString(5, request.regionId());
      ps.setString(6, request.industryId());
      ps.setString(7, request.sourceId());
      ps.setString(8, IntelligenceStore.sha256(request.content()));
      return ps;
    }, keyHolder);
    return find(generatedId(keyHolder));
  }

  public PaidIntelligenceItem approve(long id) {
    int updated = jdbcTemplate.update("update paid_intelligence set status = 'APPROVED', update_time = current_timestamp where id = ?", id);
    if (updated == 0) {
      throw new IllegalArgumentException("paid intelligence not found");
    }
    return find(id);
  }

  public List<PaidIntelligenceItem> listFor(UserAccount user) {
    if (user.role().name().equals("SUPER_ADMIN") || user.role().name().equals("OPERATOR")) {
      return jdbcTemplate.query(baseSelect() + " order by id", mapper());
    }
    if (!"SEED_PAID".equals(user.membershipLevel())) {
      return List.of();
    }
    return jdbcTemplate.query(baseSelect() + """
         where status = 'APPROVED'
           and region_id = ?
           and industry_id = ?
         order by id
        """, mapper(), user.regionId(), user.industryId());
  }

  private PaidIntelligenceItem find(long id) {
    return jdbcTemplate.query(baseSelect() + " where id = ?", mapper(), id).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("paid intelligence not found"));
  }

  private String baseSelect() {
    return """
        select id, title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, entitlement
          from paid_intelligence
        """;
  }

  private RowMapper<PaidIntelligenceItem> mapper() {
    return (rs, rowNum) -> new PaidIntelligenceItem(
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
        rs.getDouble("weight"),
        rs.getString("entitlement"));
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
