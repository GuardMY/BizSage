package com.bizsage.api.messages;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class ConversationMessageStore {
  private final JdbcTemplate jdbcTemplate;

  public ConversationMessageStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long append(
      long conversationId,
      String sender,
      String messageType,
      String content,
      String sourcesJson,
      String confidence,
      String timeliness,
      String selfCheckStatus,
      String regionId,
      String industryId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into messages
            (conversation_id, sender, message_type, content, sources_json, confidence, timeliness,
             self_check_status, is_active_context, region_id, industry_id, source_id, weight)
          values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, 'conversation', 1.0)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, conversationId);
      ps.setString(2, sender);
      ps.setString(3, messageType);
      ps.setString(4, content);
      ps.setString(5, sourcesJson);
      ps.setString(6, confidence);
      ps.setString(7, timeliness);
      ps.setString(8, selfCheckStatus);
      ps.setString(9, regionId);
      ps.setString(10, industryId);
      return ps;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  public List<ConversationMessage> recentActiveMessages(long conversationId, int limit) {
    return jdbcTemplate.query("""
        select id, conversation_id, sender, message_type, content, sources_json, confidence,
               timeliness, self_check_status, is_active_context, summary_group_id, create_time
          from messages
         where conversation_id = ? and is_active_context = true
         order by id desc
         limit ?
        """, mapper(), conversationId, limit).stream().toList().reversed();
  }

  public List<ConversationMessage> activeMessages(long conversationId) {
    return jdbcTemplate.query("""
        select id, conversation_id, sender, message_type, content, sources_json, confidence,
               timeliness, self_check_status, is_active_context, summary_group_id, create_time
          from messages
         where conversation_id = ? and is_active_context = true
         order by id
        """, mapper(), conversationId);
  }

  public void markInactive(long conversationId, long maxMessageId, long summaryGroupId) {
    jdbcTemplate.update("""
        update messages
           set is_active_context = false,
               summary_group_id = ?,
               update_time = current_timestamp
         where conversation_id = ? and id <= ? and is_active_context = true
        """, summaryGroupId, conversationId, maxMessageId);
  }

  private RowMapper<ConversationMessage> mapper() {
    return (rs, rowNum) -> new ConversationMessage(
        rs.getLong("id"),
        rs.getLong("conversation_id"),
        rs.getString("sender"),
        rs.getString("message_type"),
        rs.getString("content"),
        rs.getString("sources_json"),
        rs.getString("confidence"),
        rs.getString("timeliness"),
        rs.getString("self_check_status"),
        rs.getBoolean("is_active_context"),
        (Long) rs.getObject("summary_group_id"),
        rs.getTimestamp("create_time").toInstant());
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
