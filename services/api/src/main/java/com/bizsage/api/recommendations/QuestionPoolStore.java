package com.bizsage.api.recommendations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuestionPoolStore {
  private final QuestionPoolMapper mapper;

  public QuestionPoolStore(QuestionPoolMapper mapper) {
    this.mapper = mapper;
  }

  public List<QuestionPoolItem> list(String industryId, String regionId, String agentMode) {
    return mapper.selectList(new LambdaQueryWrapper<QuestionPoolItem>()
        .eq(industryId != null, QuestionPoolItem::getIndustryId, industryId)
        .eq(regionId != null, QuestionPoolItem::getRegionId, regionId)
        .eq(agentMode != null, QuestionPoolItem::getAgentMode, agentMode)
        .eq(QuestionPoolItem::getStatus, "ENABLED")
        .orderByDesc(QuestionPoolItem::getTopLevelScore)
        .orderByDesc(QuestionPoolItem::getUsageCount)
        .orderByDesc(QuestionPoolItem::getRatingAvg)
        .orderByDesc(QuestionPoolItem::getLastUsedAt)
        .last("limit 50"));
  }

  public QuestionPoolItem upsert(QuestionPoolItem item) {
    QuestionPoolItem existing = mapper.selectOne(new LambdaQueryWrapper<QuestionPoolItem>()
        .eq(QuestionPoolItem::getIndustryId, item.getIndustryId())
        .eq(QuestionPoolItem::getRegionId, item.getRegionId())
        .eq(QuestionPoolItem::getAgentMode, item.getAgentMode())
        .eq(QuestionPoolItem::getQuestionKey, item.getQuestionKey())
        .last("limit 1"));
    Instant now = Instant.now();
    if (existing == null) {
      item.setUsageCount(item.getUsageCount() == null ? 0L : item.getUsageCount());
      item.setRatingCount(item.getRatingCount() == null ? 0L : item.getRatingCount());
      item.setTopLevelScore(item.getTopLevelScore() == null ? 0.5D : item.getTopLevelScore());
      item.setStatus(item.getStatus() == null ? "ENABLED" : item.getStatus());
      item.setSourceType(item.getSourceType() == null ? "seed" : item.getSourceType());
      item.setSourceRef(item.getSourceRef() == null ? "" : item.getSourceRef());
      item.setLastUsedAt(item.getLastUsedAt() == null ? now : item.getLastUsedAt());
      item.setCreateTime(now);
      item.setUpdateTime(now);
      mapper.insert(item);
      return item;
    }
    mapper.update(null, new LambdaUpdateWrapper<QuestionPoolItem>()
        .eq(QuestionPoolItem::getId, existing.getId())
        .set(QuestionPoolItem::getCategory, item.getCategory())
        .set(QuestionPoolItem::getQuestionText, item.getQuestionText())
        .set(item.getTopLevelScore() != null, QuestionPoolItem::getTopLevelScore, item.getTopLevelScore())
        .set(item.getStatus() != null, QuestionPoolItem::getStatus, item.getStatus())
        .set(item.getSourceType() != null, QuestionPoolItem::getSourceType, item.getSourceType())
        .set(item.getSourceRef() != null, QuestionPoolItem::getSourceRef, item.getSourceRef())
        .set(QuestionPoolItem::getUpdateTime, now));
    return mapper.selectById(existing.getId());
  }

  public void recordUsage(List<Long> ids) {
    if (ids == null) return;
    for (Long id : ids) {
      if (id == null) continue;
      mapper.update(null, new LambdaUpdateWrapper<QuestionPoolItem>()
          .eq(QuestionPoolItem::getId, id)
          .setSql("usage_count = usage_count + 1")
          .set(QuestionPoolItem::getLastUsedAt, Instant.now()));
    }
  }

  public void rate(long id, double rating) {
    QuestionPoolItem item = mapper.selectById(id);
    if (item == null) {
      return;
    }
    long nextRatingCount = item.ratingCount() + 1;
    double nextRatingAvg = ((item.ratingAvg() * item.ratingCount()) + rating) / nextRatingCount;
    mapper.update(null, new LambdaUpdateWrapper<QuestionPoolItem>()
        .eq(QuestionPoolItem::getId, id)
        .set(QuestionPoolItem::getRatingAvg, nextRatingAvg)
        .set(QuestionPoolItem::getRatingCount, nextRatingCount)
        .set(QuestionPoolItem::getUpdateTime, Instant.now()));
  }

  public List<QuestionPoolItem> refreshScope(String industryId, String regionId, String agentMode) {
    return new ArrayList<>(list(industryId, regionId, agentMode));
  }
}
