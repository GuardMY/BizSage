package com.bizsage.api.conversations;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("conversations")
public class Conversation {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String ownerUsername;
  private String title;
  private String status;
  private String regionId;
  private String industryId;
  private String sourceId;
  private Double weight;
  private Long userId;
  private String agentMode;
  private String workflowStage;
  private Double profileCompleteness;
  private String primaryIssueTags;
  private String recommendedQuestionIds;
  private String closedBy;
  private String closedReason;

  public Conversation() {
  }

  public Conversation(Long id, String ownerUsername, String title, String status,
      String regionId, String industryId, String sourceId, Double weight) {
    this.id = id;
    this.ownerUsername = ownerUsername;
    this.title = title;
    this.status = status;
    this.regionId = regionId;
    this.industryId = industryId;
    this.sourceId = sourceId;
    this.weight = weight;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getOwnerUsername() {
    return ownerUsername;
  }

  public void setOwnerUsername(String ownerUsername) {
    this.ownerUsername = ownerUsername;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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

  public String getAgentMode() {
    return agentMode;
  }

  public void setAgentMode(String agentMode) {
    this.agentMode = agentMode;
  }

  public String getWorkflowStage() {
    return workflowStage;
  }

  public void setWorkflowStage(String workflowStage) {
    this.workflowStage = workflowStage;
  }

  public Double getProfileCompleteness() {
    return profileCompleteness;
  }

  public void setProfileCompleteness(Double profileCompleteness) {
    this.profileCompleteness = profileCompleteness;
  }

  public String getPrimaryIssueTags() {
    return primaryIssueTags;
  }

  public void setPrimaryIssueTags(String primaryIssueTags) {
    this.primaryIssueTags = primaryIssueTags;
  }

  public String getRecommendedQuestionIds() {
    return recommendedQuestionIds;
  }

  public void setRecommendedQuestionIds(String recommendedQuestionIds) {
    this.recommendedQuestionIds = recommendedQuestionIds;
  }

  public String getClosedBy() {
    return closedBy;
  }

  public void setClosedBy(String closedBy) {
    this.closedBy = closedBy;
  }

  public String getClosedReason() {
    return closedReason;
  }

  public void setClosedReason(String closedReason) {
    this.closedReason = closedReason;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public long id() {
    return id == null ? 0L : id;
  }

  public String ownerUsername() {
    return ownerUsername;
  }

  public String title() {
    return title;
  }

  public String status() {
    return status;
  }

  public String regionId() {
    return regionId;
  }

  public String industryId() {
    return industryId;
  }

  public String sourceId() {
    return sourceId;
  }

  public double weight() {
    return weight == null ? 0D : weight;
  }

  public String agentMode() {
    return agentMode;
  }

  public String workflowStage() {
    return workflowStage;
  }

  public double profileCompleteness() {
    return profileCompleteness == null ? 0D : profileCompleteness;
  }

  public String primaryIssueTags() {
    return primaryIssueTags;
  }

  public String recommendedQuestionIds() {
    return recommendedQuestionIds;
  }

  public String closedBy() {
    return closedBy;
  }

  public String closedReason() {
    return closedReason;
  }

  Conversation archive() {
    return new Conversation(id, ownerUsername, title, "ARCHIVED", regionId, industryId, sourceId, weight);
  }
}
