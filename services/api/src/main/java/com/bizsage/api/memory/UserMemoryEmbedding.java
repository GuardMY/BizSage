package com.bizsage.api.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_memory_embeddings")
public class UserMemoryEmbedding {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long userMemoryProfileId;
  private Long userId;
  private String memoryText;
  private String embeddingStatus;
  private Long sourceConversationId;
  private Long sourceMessageId;
  private String status;
  private String qdrantPointId;
  private Instant lastSyncedAt;

  public UserMemoryEmbedding() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserMemoryProfileId() {
    return userMemoryProfileId;
  }

  public void setUserMemoryProfileId(Long userMemoryProfileId) {
    this.userMemoryProfileId = userMemoryProfileId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getMemoryText() {
    return memoryText;
  }

  public void setMemoryText(String memoryText) {
    this.memoryText = memoryText;
  }

  public String getEmbeddingStatus() {
    return embeddingStatus;
  }

  public void setEmbeddingStatus(String embeddingStatus) {
    this.embeddingStatus = embeddingStatus;
  }

  public Long getSourceConversationId() {
    return sourceConversationId;
  }

  public void setSourceConversationId(Long sourceConversationId) {
    this.sourceConversationId = sourceConversationId;
  }

  public Long getSourceMessageId() {
    return sourceMessageId;
  }

  public void setSourceMessageId(Long sourceMessageId) {
    this.sourceMessageId = sourceMessageId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getQdrantPointId() {
    return qdrantPointId;
  }

  public void setQdrantPointId(String qdrantPointId) {
    this.qdrantPointId = qdrantPointId;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public void setLastSyncedAt(Instant lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
  }
}
