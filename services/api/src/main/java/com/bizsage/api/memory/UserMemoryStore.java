package com.bizsage.api.memory;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class UserMemoryStore {
  private final JdbcTemplate jdbcTemplate;

  public UserMemoryStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<UserMemoryProfile> activeMemoriesForUser(long userId) {
    return jdbcTemplate.query("""
        select id, user_id, memory_category, memory_key, memory_value, value_type, confidence,
               source_conversation_id, source_message_id, last_used_at, expires_at, status
          from user_memory_profiles
         where user_id = ?
           and status = 'ACTIVE'
           and (expires_at is null or expires_at > current_timestamp)
         order by confidence desc, id asc
        """, mapper(), userId);
  }

  public void markUsed(List<Long> memoryIds) {
    if (memoryIds.isEmpty()) {
      return;
    }
    for (Long id : memoryIds) {
      jdbcTemplate.update("""
          update user_memory_profiles
             set last_used_at = current_timestamp,
                 update_time = current_timestamp
           where id = ?
          """, id);
    }
  }

  public UserMemoryProfile saveOrRefresh(
      long userId,
      String category,
      String key,
      String value,
      String valueType,
      double confidence,
      long sourceConversationId,
      long sourceMessageId,
      boolean structured) {
    List<Long> existing = jdbcTemplate.query("""
        select id
          from user_memory_profiles
         where user_id = ? and memory_category = ? and memory_key = ? and status = 'ACTIVE'
        """, (rs, rowNum) -> rs.getLong("id"), userId, category, key);
    if (!existing.isEmpty()) {
      jdbcTemplate.update("""
          update user_memory_profiles
             set memory_value = ?,
                 value_type = ?,
                 confidence = ?,
                 source_conversation_id = ?,
                 source_message_id = ?,
                 expires_at = ?,
                 update_time = current_timestamp
           where id = ?
          """, value, valueType, confidence, sourceConversationId, sourceMessageId, expiryFor(category), existing.getFirst());
      return get(existing.getFirst());
    }
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into user_memory_profiles
            (user_id, memory_category, memory_key, memory_value, value_type, confidence,
             source_conversation_id, source_message_id, expires_at, status)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, userId);
      ps.setString(2, category);
      ps.setString(3, key);
      ps.setString(4, value);
      ps.setString(5, valueType);
      ps.setDouble(6, confidence);
      ps.setLong(7, sourceConversationId);
      ps.setLong(8, sourceMessageId);
      Instant expiresAt = expiryFor(category);
      if (expiresAt == null) {
        ps.setObject(9, null);
      } else {
        ps.setTimestamp(9, java.sql.Timestamp.from(expiresAt));
      }
      return ps;
    }, keyHolder);
    return get(generatedId(keyHolder));
  }

  private UserMemoryProfile get(long id) {
    return jdbcTemplate.query("""
        select id, user_id, memory_category, memory_key, memory_value, value_type, confidence,
               source_conversation_id, source_message_id, last_used_at, expires_at, status
          from user_memory_profiles
         where id = ?
        """, mapper(), id).stream().findFirst().orElseThrow();
  }

  private Instant expiryFor(String category) {
    return switch (category) {
      case "BUSINESS_FACT" -> Instant.now().plus(90, ChronoUnit.DAYS);
      case "PREFERENCE" -> Instant.now().plus(180, ChronoUnit.DAYS);
      default -> null;
    };
  }

  private RowMapper<UserMemoryProfile> mapper() {
    return (rs, rowNum) -> new UserMemoryProfile(
        rs.getLong("id"),
        rs.getLong("user_id"),
        rs.getString("memory_category"),
        rs.getString("memory_key"),
        rs.getString("memory_value"),
        rs.getString("value_type"),
        rs.getDouble("confidence"),
        (Long) rs.getObject("source_conversation_id"),
        (Long) rs.getObject("source_message_id"),
        rs.getTimestamp("last_used_at") == null ? null : rs.getTimestamp("last_used_at").toInstant(),
        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
        rs.getString("status"));
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
