package com.bizsage.api.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("false_information_ledger")
public class FalseLedgerEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long originalIntelligenceId;
  private String title;
  private String content;
  private String contentHash;
  private String conflictReason;
  private String matchedRumorKeyword;
  private String sourceId;
  private String regionId;
  private String industryId;
  private String linkId;
  private String archivedBy;
  private String archiveReason;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getOriginalIntelligenceId() { return originalIntelligenceId; }
  public void setOriginalIntelligenceId(Long originalIntelligenceId) { this.originalIntelligenceId = originalIntelligenceId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getContentHash() { return contentHash; }
  public void setContentHash(String contentHash) { this.contentHash = contentHash; }
  public String getConflictReason() { return conflictReason; }
  public void setConflictReason(String conflictReason) { this.conflictReason = conflictReason; }
  public String getMatchedRumorKeyword() { return matchedRumorKeyword; }
  public void setMatchedRumorKeyword(String matchedRumorKeyword) { this.matchedRumorKeyword = matchedRumorKeyword; }
  public String getSourceId() { return sourceId; }
  public void setSourceId(String sourceId) { this.sourceId = sourceId; }
  public String getRegionId() { return regionId; }
  public void setRegionId(String regionId) { this.regionId = regionId; }
  public String getIndustryId() { return industryId; }
  public void setIndustryId(String industryId) { this.industryId = industryId; }
  public String getLinkId() { return linkId; }
  public void setLinkId(String linkId) { this.linkId = linkId; }
  public String getArchivedBy() { return archivedBy; }
  public void setArchivedBy(String archivedBy) { this.archivedBy = archivedBy; }
  public String getArchiveReason() { return archiveReason; }
  public void setArchiveReason(String archiveReason) { this.archiveReason = archiveReason; }
}
