package com.bizsage.api.intelligence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("admin_intelligence_reviews")
public class ReviewTicket {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long intelligenceId;
  private String reviewStatus;
  private String verdict;
  private String reviewer;
  private String reason;
  private String sourceId;
  private Double weight;
  private String regionId;
  private String industryId;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  public ReviewTicket() {
  }

  public ReviewTicket(Long id, Long intelligenceId, String reviewStatus, String verdict,
      String reviewer, String reason, String sourceId, Double weight, String regionId,
      String industryId, LocalDateTime createTime, LocalDateTime updateTime) {
    this.id = id;
    this.intelligenceId = intelligenceId;
    this.reviewStatus = reviewStatus;
    this.verdict = verdict;
    this.reviewer = reviewer;
    this.reason = reason;
    this.sourceId = sourceId;
    this.weight = weight;
    this.regionId = regionId;
    this.industryId = industryId;
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getIntelligenceId() {
    return intelligenceId;
  }

  public void setIntelligenceId(Long intelligenceId) {
    this.intelligenceId = intelligenceId;
  }

  public String getReviewStatus() {
    return reviewStatus;
  }

  public void setReviewStatus(String reviewStatus) {
    this.reviewStatus = reviewStatus;
  }

  public String getVerdict() {
    return verdict;
  }

  public void setVerdict(String verdict) {
    this.verdict = verdict;
  }

  public String getReviewer() {
    return reviewer;
  }

  public void setReviewer(String reviewer) {
    this.reviewer = reviewer;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
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

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }

  public long id() {
    return id == null ? 0L : id;
  }

  public long intelligenceId() {
    return intelligenceId == null ? 0L : intelligenceId;
  }

  public String reviewStatus() {
    return reviewStatus;
  }

  public String verdict() {
    return verdict;
  }

  public String reviewer() {
    return reviewer;
  }

  public String reason() {
    return reason;
  }

  public String sourceId() {
    return sourceId;
  }

  public double weight() {
    return weight == null ? 0D : weight;
  }

  public String regionId() {
    return regionId;
  }

  public String industryId() {
    return industryId;
  }

  public LocalDateTime createTime() {
    return createTime;
  }

  public LocalDateTime updateTime() {
    return updateTime;
  }
}
