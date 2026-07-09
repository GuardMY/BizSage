package com.bizsage.api.intelligence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("intelligence")
public class IntelligenceItem {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String title;
  private String content;
  private String url;
  private String status;
  private Double confidence;
  private String linkId;
  private String regionId;
  private String industryId;
  private String sourceId;
  private Double weight;
  @TableField(exist = false)
  private String entitlement = "FREE";
  private String contentHash;

  public IntelligenceItem() {
  }

  public IntelligenceItem(Long id, String title, String content, String url, String status,
      Double confidence, String linkId, String regionId, String industryId,
      String sourceId, Double weight, String entitlement) {
    this.id = id;
    this.title = title;
    this.content = content;
    this.url = url;
    this.status = status;
    this.confidence = confidence;
    this.linkId = linkId;
    this.regionId = regionId;
    this.industryId = industryId;
    this.sourceId = sourceId;
    this.weight = weight;
    this.entitlement = entitlement;
  }

  public IntelligenceItem(Long id, String title, String content, String url, String status,
      Double confidence, String linkId, String regionId, String industryId,
      String sourceId, Double weight) {
    this(id, title, content, url, status, confidence, linkId, regionId, industryId, sourceId, weight, "FREE");
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Double getConfidence() {
    return confidence;
  }

  public void setConfidence(Double confidence) {
    this.confidence = confidence;
  }

  public String getLinkId() {
    return linkId;
  }

  public void setLinkId(String linkId) {
    this.linkId = linkId;
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

  public String getEntitlement() {
    return entitlement;
  }

  public void setEntitlement(String entitlement) {
    this.entitlement = entitlement;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public long id() {
    return id == null ? 0L : id;
  }

  public String title() {
    return title;
  }

  public String content() {
    return content;
  }

  public String url() {
    return url;
  }

  public String status() {
    return status;
  }

  public double confidence() {
    return confidence == null ? 0D : confidence;
  }

  public String linkId() {
    return linkId;
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

  public String entitlement() {
    return entitlement;
  }

  IntelligenceItem approve() {
    return new IntelligenceItem(id, title, content, url, "APPROVED", confidence, linkId, regionId,
        industryId, sourceId, weight, entitlement);
  }
}
