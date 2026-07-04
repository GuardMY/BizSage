package com.bizsage.api.conversations;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class ConversationStore {
  private final JdbcTemplate jdbcTemplate;

  public ConversationStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Conversation create(String ownerUsername, String title, String regionId, String industryId) {
    Long userId = jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, ownerUsername);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into conversations (user_id, owner_username, title, status, region_id, industry_id, source_id, weight)
          values (?, ?, ?, 'ACTIVE', ?, ?, 'user', 1.0)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, userId == null ? 0 : userId);
      ps.setString(2, ownerUsername);
      ps.setString(3, title);
      ps.setString(4, regionId);
      ps.setString(5, industryId);
      return ps;
    }, keyHolder);
    return findForOwner(ownerUsername, generatedId(keyHolder));
  }

  public List<Conversation> listFor(String ownerUsername) {
    return jdbcTemplate.query("""
        select id, owner_username, title, status, region_id, industry_id, source_id, weight
          from conversations
         where owner_username = ?
         order by id
        """, mapper(), ownerUsername);
  }

  public Conversation archive(String ownerUsername, long id) {
    int updated = jdbcTemplate.update("""
        update conversations
           set status = 'ARCHIVED', update_time = current_timestamp
         where id = ? and owner_username = ?
        """, id, ownerUsername);
    if (updated == 0) {
      throw new IllegalArgumentException("conversation not found");
    }
    return findForOwner(ownerUsername, id);
  }

  private Conversation findForOwner(String ownerUsername, long id) {
    return jdbcTemplate.query("""
        select id, owner_username, title, status, region_id, industry_id, source_id, weight
          from conversations
         where id = ? and owner_username = ?
        """, mapper(), id, ownerUsername).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("conversation not found"));
  }

  private RowMapper<Conversation> mapper() {
    return (rs, rowNum) -> new Conversation(
        rs.getLong("id"),
        rs.getString("owner_username"),
        rs.getString("title"),
        rs.getString("status"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("source_id"),
        rs.getDouble("weight"));
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
