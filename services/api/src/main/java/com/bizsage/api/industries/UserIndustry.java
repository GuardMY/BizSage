package com.bizsage.api.industries;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_industries")
public class UserIndustry {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Long userId;
  private String industryId;
  private String industryName;
  private String sourceType;
  private String status;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getIndustryId() { return industryId; }
  public void setIndustryId(String industryId) { this.industryId = industryId; }
  public String getIndustryName() { return industryName; }
  public void setIndustryName(String industryName) { this.industryName = industryName; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
