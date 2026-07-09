package com.bizsage.api.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_memory_profiles")
public class UserMemoryProfile {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long userId;
  @TableField("memory_category")
  private String category;
  @TableField("memory_key")
  private String memoryKey;
  @TableField("memory_value")
  private String memoryValue;
  private String valueType;
  private Double confidence;
  private Long sourceConversationId;
  private Long sourceMessageId;
  private Instant lastUsedAt;
  private Instant expiresAt;
  private String status;

  public UserMemoryProfile() {
  }

  public UserMemoryProfile(Long id, Long userId, String category, String key, String value,
      String valueType, Double confidence, Long sourceConversationId, Long sourceMessageId,
      Instant lastUsedAt, Instant expiresAt, String status) {
    this.id = id;
    this.userId = userId;
    this.category = category;
    this.key = key;
    this.value = value;
    this.valueType = valueType;
    this.confidence = confidence;
    this.sourceConversationId = sourceConversationId;
    this.sourceMessageId = sourceMessageId;
    this.lastUsedAt = lastUsedAt;
    this.expiresAt = expiresAt;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getKey() {
    return memoryKey;
  }

  public void setKey(String key) {
    this.memoryKey = key;
  }

  public String getMemoryKey() {
    return memoryKey;
  }

  public void setMemoryKey(String memoryKey) {
    this.memoryKey = memoryKey;
  }

  public String getValue() {
    return memoryValue;
  }

  public void setValue(String value) {
    this.memoryValue = value;
  }

  public String getMemoryValue() {
    return memoryValue;
  }

  public void setMemoryValue(String memoryValue) {
    this.memoryValue = memoryValue;
  }

  public String getValueType() {
    return valueType;
  }

  public void setValueType(String valueType) {
    this.valueType = valueType;
  }

  public Double getConfidence() {
    return confidence;
  }

  public void setConfidence(Double confidence) {
    this.confidence = confidence;
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

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long id() {
    return id == null ? 0L : id;
  }

  public long userId() {
    return userId == null ? 0L : userId;
  }

  public String category() {
    return category;
  }

  public String key() {
    return memoryKey;
  }

  public String value() {
    return memoryValue;
  }

  public String valueType() {
    return valueType;
  }

  public double confidence() {
    return confidence == null ? 0D : confidence;
  }

  public Long sourceConversationId() {
    return sourceConversationId;
  }

  public Long sourceMessageId() {
    return sourceMessageId;
  }

  public Instant lastUsedAt() {
    return lastUsedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public String status() {
    return status;
  }
}
