package com.bizsage.api.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("conflict_resolutions")
public class ConflictResolutionEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long incomingIntelligenceId;
  private Long existingIntelligenceId;
  private String conflictBranch;
  private Integer simHashDistance;
  private Double incomingWeight;
  private Double existingWeight;
  private String routingAction;
  private Long reviewTicketId;
  private String resolvedBy;
  private String notes;
  private String regionId;
  private String industryId;
  private String linkId;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getIncomingIntelligenceId() { return incomingIntelligenceId; }
  public void setIncomingIntelligenceId(Long incomingIntelligenceId) { this.incomingIntelligenceId = incomingIntelligenceId; }
  public Long getExistingIntelligenceId() { return existingIntelligenceId; }
  public void setExistingIntelligenceId(Long existingIntelligenceId) { this.existingIntelligenceId = existingIntelligenceId; }
  public String getConflictBranch() { return conflictBranch; }
  public void setConflictBranch(String conflictBranch) { this.conflictBranch = conflictBranch; }
  public Integer getSimHashDistance() { return simHashDistance; }
  public void setSimHashDistance(Integer simHashDistance) { this.simHashDistance = simHashDistance; }
  public Double getIncomingWeight() { return incomingWeight; }
  public void setIncomingWeight(Double incomingWeight) { this.incomingWeight = incomingWeight; }
  public Double getExistingWeight() { return existingWeight; }
  public void setExistingWeight(Double existingWeight) { this.existingWeight = existingWeight; }
  public String getRoutingAction() { return routingAction; }
  public void setRoutingAction(String routingAction) { this.routingAction = routingAction; }
  public Long getReviewTicketId() { return reviewTicketId; }
  public void setReviewTicketId(Long reviewTicketId) { this.reviewTicketId = reviewTicketId; }
  public String getResolvedBy() { return resolvedBy; }
  public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
  public String getRegionId() { return regionId; }
  public void setRegionId(String regionId) { this.regionId = regionId; }
  public String getIndustryId() { return industryId; }
  public void setIndustryId(String industryId) { this.industryId = industryId; }
  public String getLinkId() { return linkId; }
  public void setLinkId(String linkId) { this.linkId = linkId; }
}
