package com.bizsage.api.messages;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("messages")
public class ConversationMessage {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long conversationId;
  private String sender;
  private String messageType;
  private String content;
  private String sourcesJson;
  private String confidence;
  private String timeliness;
  private String selfCheckStatus;
  @TableField("is_active_context")
  private Boolean activeContext;
  private Long summaryGroupId;
  private Instant createTime;
  private String regionId;
  private String industryId;
  private String sourceId;
  private Double weight;

  public ConversationMessage() {
  }

  public ConversationMessage(Long id, Long conversationId, String sender, String messageType,
      String content, String sourcesJson, String confidence, String timeliness,
      String selfCheckStatus, Boolean activeContext, Long summaryGroupId, Instant createTime) {
    this.id = id;
    this.conversationId = conversationId;
    this.sender = sender;
    this.messageType = messageType;
    this.content = content;
    this.sourcesJson = sourcesJson;
    this.confidence = confidence;
    this.timeliness = timeliness;
    this.selfCheckStatus = selfCheckStatus;
    this.activeContext = activeContext;
    this.summaryGroupId = summaryGroupId;
    this.createTime = createTime;
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

  public String getSender() {
    return sender;
  }

  public void setSender(String sender) {
    this.sender = sender;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSourcesJson() {
    return sourcesJson;
  }

  public void setSourcesJson(String sourcesJson) {
    this.sourcesJson = sourcesJson;
  }

  public String getConfidence() {
    return confidence;
  }

  public void setConfidence(String confidence) {
    this.confidence = confidence;
  }

  public String getTimeliness() {
    return timeliness;
  }

  public void setTimeliness(String timeliness) {
    this.timeliness = timeliness;
  }

  public String getSelfCheckStatus() {
    return selfCheckStatus;
  }

  public void setSelfCheckStatus(String selfCheckStatus) {
    this.selfCheckStatus = selfCheckStatus;
  }

  public Boolean getActiveContext() {
    return activeContext;
  }

  public void setActiveContext(Boolean activeContext) {
    this.activeContext = activeContext;
  }

  public Long getSummaryGroupId() {
    return summaryGroupId;
  }

  public void setSummaryGroupId(Long summaryGroupId) {
    this.summaryGroupId = summaryGroupId;
  }

  public Instant getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Instant createTime) {
    this.createTime = createTime;
  }

  public String getRegionId() {
    return regionId;
  }

  public void setRegionId(String regionId) {
    this.regionId = regionId;
  }

  public String getIndustryId() {
    return industryId;
  }

  public void setIndustryId(String industryId) {
    this.industryId = industryId;
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

  public String sender() {
    return sender;
  }

  public String messageType() {
    return messageType;
  }

  public String content() {
    return content;
  }

  public String sourcesJson() {
    return sourcesJson;
  }

  public String confidence() {
    return confidence;
  }

  public String timeliness() {
    return timeliness;
  }

  public String selfCheckStatus() {
    return selfCheckStatus;
  }

  public boolean activeContext() {
    return Boolean.TRUE.equals(activeContext);
  }

  public Long summaryGroupId() {
    return summaryGroupId;
  }

  public Instant createTime() {
    return createTime;
  }
}
