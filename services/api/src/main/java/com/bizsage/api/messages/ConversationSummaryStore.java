package com.bizsage.api.messages;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class ConversationSummaryStore {
  private final JdbcTemplate jdbcTemplate;

  public ConversationSummaryStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<ConversationSummary> latestActive(long conversationId) {
    List<ConversationSummary> items = jdbcTemplate.query("""
        select id, conversation_id, summary_text, covered_message_start_id, covered_message_end_id,
               summary_version, active
          from conversation_summaries
         where conversation_id = ? and active = true
         order by id desc
         limit 1
        """, mapper(), conversationId);
    return items.stream().findFirst();
  }

  public ConversationSummary save(long conversationId, String summaryText, long startId, long endId) {
    int nextVersion = jdbcTemplate.queryForObject("""
        select coalesce(max(summary_version), 0) + 1
          from conversation_summaries
         where conversation_id = ?
        """, Integer.class, conversationId);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into conversation_summaries
            (conversation_id, summary_text, covered_message_start_id, covered_message_end_id,
             summary_version, active, source_id, weight)
          values (?, ?, ?, ?, ?, true, 'summary', 1.0)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, conversationId);
      ps.setString(2, summaryText);
      ps.setLong(3, startId);
      ps.setLong(4, endId);
      ps.setInt(5, nextVersion);
      return ps;
    }, keyHolder);
    return new ConversationSummary(generatedId(keyHolder), conversationId, summaryText, startId, endId, nextVersion, true);
  }

  private RowMapper<ConversationSummary> mapper() {
    return (rs, rowNum) -> new ConversationSummary(
        rs.getLong("id"),
        rs.getLong("conversation_id"),
        rs.getString("summary_text"),
        rs.getLong("covered_message_start_id"),
        rs.getLong("covered_message_end_id"),
        rs.getInt("summary_version"),
        rs.getBoolean("active"));
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }
}
