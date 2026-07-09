package com.bizsage.api.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("sla_data_points")
public class SlaDataPointEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private Instant windowStart;
  private Instant windowEnd;
  private Integer totalRequests;
  private Integer errorRequests;
  private Double latencyP50Ms;
  private Double latencyP95Ms;
  private Double latencyP99Ms;
  private Integer uptimeFlag;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Instant getWindowStart() { return windowStart; }
  public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
  public Instant getWindowEnd() { return windowEnd; }
  public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
  public Integer getTotalRequests() { return totalRequests; }
  public void setTotalRequests(Integer totalRequests) { this.totalRequests = totalRequests; }
  public Integer getErrorRequests() { return errorRequests; }
  public void setErrorRequests(Integer errorRequests) { this.errorRequests = errorRequests; }
  public Double getLatencyP50Ms() { return latencyP50Ms; }
  public void setLatencyP50Ms(Double latencyP50Ms) { this.latencyP50Ms = latencyP50Ms; }
  public Double getLatencyP95Ms() { return latencyP95Ms; }
  public void setLatencyP95Ms(Double latencyP95Ms) { this.latencyP95Ms = latencyP95Ms; }
  public Double getLatencyP99Ms() { return latencyP99Ms; }
  public void setLatencyP99Ms(Double latencyP99Ms) { this.latencyP99Ms = latencyP99Ms; }
  public Integer getUptimeFlag() { return uptimeFlag; }
  public void setUptimeFlag(Integer uptimeFlag) { this.uptimeFlag = uptimeFlag; }
}
