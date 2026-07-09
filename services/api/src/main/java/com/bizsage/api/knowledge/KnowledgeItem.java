package com.bizsage.api.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("knowledge_items")
public class KnowledgeItem {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String title;
  private String content;
  private String industryId;
  private String regionId;
  private String linkId;
  private String sourceId;
  private String sourceUrl;
  private Double confidence;
  private Double weight;
  @TableField(exist = false)
  private String entitlement = "FREE";

  public KnowledgeItem() {
  }

  public KnowledgeItem(Long id, String title, String content, String industryId,
      String regionId, String linkId, String sourceId, String sourceUrl,
      Double confidence, Double weight, String entitlement) {
    this.id = id;
    this.title = title;
    this.content = content;
    this.industryId = industryId;
    this.regionId = regionId;
    this.linkId = linkId;
    this.sourceId = sourceId;
    this.sourceUrl = sourceUrl;
    this.confidence = confidence;
    this.weight = weight;
    this.entitlement = entitlement;
  }

  public KnowledgeItem(Long id, String title, String content, String industryId,
      String regionId, String linkId, String sourceId, String sourceUrl,
      Double confidence, Double weight) {
    this(id, title, content, industryId, regionId, linkId, sourceId, sourceUrl, confidence, weight, "FREE");
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

  public String getIndustryId() {
    return industryId;
  }

  public void setIndustryId(String industryId) {
    this.industryId = industryId;
  }

  public String getRegionId() {
    return regionId;
  }

  public void setRegionId(String regionId) {
    this.regionId = regionId;
  }

  public String getLinkId() {
    return linkId;
  }

  public void setLinkId(String linkId) {
    this.linkId = linkId;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public Double getConfidence() {
    return confidence;
  }

  public void setConfidence(Double confidence) {
    this.confidence = confidence;
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

  public long id() {
    return id == null ? 0L : id;
  }

  public String title() {
    return title;
  }

  public String content() {
    return content;
  }

  public String industryId() {
    return industryId;
  }

  public String regionId() {
    return regionId;
  }

  public String linkId() {
    return linkId;
  }

  public String sourceId() {
    return sourceId;
  }

  public String sourceUrl() {
    return sourceUrl;
  }

  public double confidence() {
    return confidence == null ? 0D : confidence;
  }

  public double weight() {
    return weight == null ? 0D : weight;
  }

  public String entitlement() {
    return entitlement;
  }
}
