package com.bizsage.api.messages;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("conversation_summaries")
public class ConversationSummary {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long conversationId;
  private String summaryText;
  private Long coveredMessageStartId;
  private Long coveredMessageEndId;
  private Integer summaryVersion;
  private Boolean active;
  private String sourceId;
  private Double weight;

  public ConversationSummary() {
  }

  public ConversationSummary(Long id, Long conversationId, String summaryText,
      Long coveredMessageStartId, Long coveredMessageEndId, Integer summaryVersion,
      Boolean active) {
    this.id = id;
    this.conversationId = conversationId;
    this.summaryText = summaryText;
    this.coveredMessageStartId = coveredMessageStartId;
    this.coveredMessageEndId = coveredMessageEndId;
    this.summaryVersion = summaryVersion;
    this.active = active;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public void setConversationId(Long conversationId) {
    this.conversationId = conversationId;
  }

  public String getSummaryText() {
    return summaryText;
  }

  public void setSummaryText(String summaryText) {
    this.summaryText = summaryText;
  }

  public Long getCoveredMessageStartId() {
    return coveredMessageStartId;
  }

  public void setCoveredMessageStartId(Long coveredMessageStartId) {
    this.coveredMessageStartId = coveredMessageStartId;
  }

  public Long getCoveredMessageEndId() {
    return coveredMessageEndId;
  }

  public void setCoveredMessageEndId(Long coveredMessageEndId) {
    this.coveredMessageEndId = coveredMessageEndId;
  }

  public Integer getSummaryVersion() {
    return summaryVersion;
  }

  public void setSummaryVersion(Integer summaryVersion) {
    this.summaryVersion = summaryVersion;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public Double getWeight() {
    return weight;
  }

  public void setWeight(Double weight) {
    this.weight = weight;
  }

  public long id() {
    return id == null ? 0L : id;
  }

  public long conversationId() {
    return conversationId == null ? 0L : conversationId;
  }

  public String summaryText() {
    return summaryText;
  }

  public long coveredMessageStartId() {
    return coveredMessageStartId == null ? 0L : coveredMessageStartId;
  }

  public long coveredMessageEndId() {
    return coveredMessageEndId == null ? 0L : coveredMessageEndId;
  }

  public int summaryVersion() {
    return summaryVersion == null ? 0 : summaryVersion;
  }

  public boolean active() {
    return Boolean.TRUE.equals(active);
  }
}
