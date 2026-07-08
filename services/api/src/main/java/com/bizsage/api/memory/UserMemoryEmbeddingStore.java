package com.bizsage.api.memory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserMemoryEmbeddingStore {
  private final JdbcTemplate jdbcTemplate;

  public UserMemoryEmbeddingStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(long userMemoryProfileId, long userId, String memoryText, long sourceConversationId, long sourceMessageId) {
    jdbcTemplate.update("""
        insert into user_memory_embeddings
          (user_memory_profile_id, user_id, memory_text, embedding_status, source_conversation_id, source_message_id, status)
        values (?, ?, ?, 'PENDING', ?, ?, 'ACTIVE')
        """, userMemoryProfileId, userId, memoryText, sourceConversationId, sourceMessageId);
  }

  /**
   * List pending memory embeddings that need to be synced to Qdrant.
   *
   * @param limit maximum number of records to return
   * @return list of pending embedding records as maps
   */
  public List<java.util.Map<String, Object>> listPending(int limit) {
    return jdbcTemplate.queryForList("""
        select id, user_memory_profile_id, user_id, memory_text,
               source_conversation_id, source_message_id
          from user_memory_embeddings
         where embedding_status = 'PENDING'
           and status = 'ACTIVE'
         order by id asc
         limit ?
        """, limit);
  }

  /**
   * Mark an embedding record as synced after successful Qdrant upsert.
   *
   * @param id the embedding record ID
   * @param qdrantPointId the assigned Qdrant point ID
   */
  public void markSynced(long id, String qdrantPointId) {
    jdbcTemplate.update("""
        update user_memory_embeddings
           set embedding_status = 'SYNCED',
               qdrant_point_id = ?,
               last_synced_at = ?,
               update_time = current_timestamp
         where id = ?
        """, qdrantPointId, Timestamp.from(Instant.now()), id);
  }

  /**
   * Mark an embedding record as failed after a sync error.
   *
   * @param id the embedding record ID
   */
  public void markFailed(long id) {
    jdbcTemplate.update("""
        update user_memory_embeddings
           set embedding_status = 'FAILED',
               update_time = current_timestamp
         where id = ?
        """, id);
  }
}
