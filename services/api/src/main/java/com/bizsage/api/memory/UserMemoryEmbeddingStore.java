package com.bizsage.api.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserMemoryEmbeddingStore {
  private final UserMemoryEmbeddingMapper embeddingMapper;

  public UserMemoryEmbeddingStore(UserMemoryEmbeddingMapper embeddingMapper) {
    this.embeddingMapper = embeddingMapper;
  }

  public void save(long userMemoryProfileId, long userId, String memoryText, long sourceConversationId, long sourceMessageId) {
    UserMemoryEmbedding embedding = new UserMemoryEmbedding();
    embedding.setUserMemoryProfileId(userMemoryProfileId);
    embedding.setUserId(userId);
    embedding.setMemoryText(memoryText);
    embedding.setEmbeddingStatus("PENDING");
    embedding.setSourceConversationId(sourceConversationId);
    embedding.setSourceMessageId(sourceMessageId);
    embedding.setStatus("ACTIVE");
    embeddingMapper.insert(embedding);
  }

  public List<Map<String, Object>> listPending(int limit) {
    return embeddingMapper.selectList(new LambdaQueryWrapper<UserMemoryEmbedding>()
        .eq(UserMemoryEmbedding::getEmbeddingStatus, "PENDING")
        .eq(UserMemoryEmbedding::getStatus, "ACTIVE")
        .orderByAsc(UserMemoryEmbedding::getId)
        .last("limit " + limit)).stream()
        .map(item -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("id", item.getId());
          row.put("user_memory_profile_id", item.getUserMemoryProfileId());
          row.put("user_id", item.getUserId());
          row.put("memory_text", item.getMemoryText());
          row.put("source_conversation_id", item.getSourceConversationId());
          row.put("source_message_id", item.getSourceMessageId());
          return row;
        })
        .toList();
  }

  public void markSynced(long id, String qdrantPointId) {
    embeddingMapper.update(null, new LambdaUpdateWrapper<UserMemoryEmbedding>()
        .eq(UserMemoryEmbedding::getId, id)
        .set(UserMemoryEmbedding::getEmbeddingStatus, "SYNCED")
        .set(UserMemoryEmbedding::getQdrantPointId, qdrantPointId)
        .set(UserMemoryEmbedding::getLastSyncedAt, Instant.now()));
  }

  public void markFailed(long id) {
    embeddingMapper.update(null, new LambdaUpdateWrapper<UserMemoryEmbedding>()
        .eq(UserMemoryEmbedding::getId, id)
        .set(UserMemoryEmbedding::getEmbeddingStatus, "FAILED"));
  }
}
