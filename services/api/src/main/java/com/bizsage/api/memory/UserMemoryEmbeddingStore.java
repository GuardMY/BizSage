package com.bizsage.api.memory;

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
}
