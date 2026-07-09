package com.bizsage.api.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("intelligence_snapshots")
public class SnapshotEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String snapshotType;
  private String scopeKey;
  private String payloadJson;
  private String sourceId;
  private Double weight;
  private String regionId;
  private String industryId;
  private Integer retentionDays;
  private Integer recordCount;
  private Long parentSnapshotId;
  private LocalDateTime expiresAt;
  private LocalDateTime createTime;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getSnapshotType() { return snapshotType; }
  public void setSnapshotType(String snapshotType) { this.snapshotType = snapshotType; }
  public String getScopeKey() { return scopeKey; }
  public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
  public String getPayloadJson() { return payloadJson; }
  public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
  public String getSourceId() { return sourceId; }
  public void setSourceId(String sourceId) { this.sourceId = sourceId; }
  public Double getWeight() { return weight; }
  public void setWeight(Double weight) { this.weight = weight; }
  public String getRegionId() { return regionId; }
  public void setRegionId(String regionId) { this.regionId = regionId; }
  public String getIndustryId() { return industryId; }
  public void setIndustryId(String industryId) { this.industryId = industryId; }
  public Integer getRetentionDays() { return retentionDays; }
  public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
  public Integer getRecordCount() { return recordCount; }
  public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
  public Long getParentSnapshotId() { return parentSnapshotId; }
  public void setParentSnapshotId(Long parentSnapshotId) { this.parentSnapshotId = parentSnapshotId; }
  public LocalDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
  public LocalDateTime getCreateTime() { return createTime; }
  public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
