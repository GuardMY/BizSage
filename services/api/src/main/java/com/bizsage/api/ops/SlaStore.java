package com.bizsage.api.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class SlaStore {
  private final SlaDataPointMapper slaDataPointMapper;

  public SlaStore(SlaDataPointMapper slaDataPointMapper) {
    this.slaDataPointMapper = slaDataPointMapper;
  }

  public void insert(SlaDataPoint point) {
    SlaDataPointEntity entity = new SlaDataPointEntity();
    entity.setWindowStart(point.windowStart());
    entity.setWindowEnd(point.windowEnd());
    entity.setTotalRequests(point.totalRequests());
    entity.setErrorRequests(point.errorRequests());
    entity.setLatencyP50Ms(point.latencyP50Ms());
    entity.setLatencyP95Ms(point.latencyP95Ms());
    entity.setLatencyP99Ms(point.latencyP99Ms());
    entity.setUptimeFlag(point.uptimeFlag() ? 1 : 0);
    slaDataPointMapper.insert(entity);
  }

  public List<Map<String, Object>> queryWindow(Instant since, Instant until) {
    return slaDataPointMapper.selectMaps(new LambdaQueryWrapper<SlaDataPointEntity>()
        .ge(SlaDataPointEntity::getWindowStart, since)
        .le(SlaDataPointEntity::getWindowStart, until)
        .orderByAsc(SlaDataPointEntity::getWindowStart));
  }

  public int deleteOlderThan(Instant cutoff) {
    return slaDataPointMapper.delete(new LambdaQueryWrapper<SlaDataPointEntity>()
        .lt(SlaDataPointEntity::getWindowStart, cutoff));
  }

  public record SlaDataPoint(
      Instant windowStart,
      Instant windowEnd,
      int totalRequests,
      int errorRequests,
      Double latencyP50Ms,
      Double latencyP95Ms,
      Double latencyP99Ms,
      boolean uptimeFlag) {
  }
}
