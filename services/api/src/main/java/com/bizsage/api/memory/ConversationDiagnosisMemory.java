package com.bizsage.api.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("conversation_diagnosis_memories")
public class ConversationDiagnosisMemory {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long conversationId;
  private String category;
  private String memoryKey;
  private String memoryValue;
  private Double confidence;
  private Boolean structured;
  private Long sourceMessageId;
  private String status;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getConversationId() { return conversationId; }
  public void setConversationId(Long value) { conversationId = value; }
  public String getCategory() { return category; }
  public void setCategory(String value) { category = value; }
  public String getMemoryKey() { return memoryKey; }
  public void setMemoryKey(String value) { memoryKey = value; }
  public String getMemoryValue() { return memoryValue; }
  public void setMemoryValue(String value) { memoryValue = value; }
  public Double getConfidence() { return confidence; }
  public void setConfidence(Double value) { confidence = value; }
  public Boolean getStructured() { return structured; }
  public void setStructured(Boolean value) { structured = value; }
  public Long getSourceMessageId() { return sourceMessageId; }
  public void setSourceMessageId(Long value) { sourceMessageId = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
}
