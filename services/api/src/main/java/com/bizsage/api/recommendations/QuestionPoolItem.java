package com.bizsage.api.recommendations;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("question_pools")
public class QuestionPoolItem {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String industryId;
  private String regionId;
  private String agentMode;
  private String questionKey;
  private String category;
  private String questionText;
  private Double topLevelScore;
  private Long usageCount;
  private Double ratingAvg;
  private Long ratingCount;
  private Instant lastUsedAt;
  private String status;
  private String sourceType;
  private String sourceRef;
  private Instant createTime;
  private Instant updateTime;

  public long id() { return id == null ? 0L : id; }
  public String industryId() { return industryId; }
  public String regionId() { return regionId; }
  public String agentMode() { return agentMode; }
  public String questionKey() { return questionKey; }
  public String category() { return category; }
  public String questionText() { return questionText; }
  public double topLevelScore() { return topLevelScore == null ? 0D : topLevelScore; }
  public long usageCount() { return usageCount == null ? 0L : usageCount; }
  public double ratingAvg() { return ratingAvg == null ? 0D : ratingAvg; }
  public long ratingCount() { return ratingCount == null ? 0L : ratingCount; }
  public Instant lastUsedAt() { return lastUsedAt; }
  public String status() { return status; }
  public String sourceType() { return sourceType; }
  public String sourceRef() { return sourceRef; }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getIndustryId() { return industryId; }
  public void setIndustryId(String industryId) { this.industryId = industryId; }
  public String getRegionId() { return regionId; }
  public void setRegionId(String regionId) { this.regionId = regionId; }
  public String getAgentMode() { return agentMode; }
  public void setAgentMode(String agentMode) { this.agentMode = agentMode; }
  public String getQuestionKey() { return questionKey; }
  public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getQuestionText() { return questionText; }
  public void setQuestionText(String questionText) { this.questionText = questionText; }
  public Double getTopLevelScore() { return topLevelScore; }
  public void setTopLevelScore(Double topLevelScore) { this.topLevelScore = topLevelScore; }
  public Long getUsageCount() { return usageCount; }
  public void setUsageCount(Long usageCount) { this.usageCount = usageCount; }
  public Double getRatingAvg() { return ratingAvg; }
  public void setRatingAvg(Double ratingAvg) { this.ratingAvg = ratingAvg; }
  public Long getRatingCount() { return ratingCount; }
  public void setRatingCount(Long ratingCount) { this.ratingCount = ratingCount; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getSourceRef() { return sourceRef; }
  public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
  public Instant getCreateTime() { return createTime; }
  public void setCreateTime(Instant createTime) { this.createTime = createTime; }
  public Instant getUpdateTime() { return updateTime; }
  public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
}

