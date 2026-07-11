package com.bizsage.api.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeStore {
  private final KnowledgeMapper knowledgeMapper;

  public KnowledgeStore(KnowledgeMapper knowledgeMapper) {
    this.knowledgeMapper = knowledgeMapper;
  }

  public KnowledgeItem importItem(ImportKnowledgeRequest request) {
    KnowledgeItem item = new KnowledgeItem();
    item.setTitle(request.title());
    item.setContent(request.content());
    item.setConfidence(0.85D);
    item.setLinkId(request.linkId());
    item.setRegionId(request.regionId());
    item.setIndustryId(request.industryId());
    item.setSourceId(request.sourceId());
    item.setWeight(0.85D);
    item.setEntitlement("FREE");
    knowledgeMapper.insert(item);
    return find(item.id());
  }

  public List<KnowledgeItem> list() {
    return knowledgeMapper.selectList(new LambdaQueryWrapper<KnowledgeItem>()
        .orderByAsc(KnowledgeItem::getId));
  }

  /** Returns knowledge items scoped to the given region and industry. */
  public List<KnowledgeItem> listScoped(String regionId, String industryId) {
    LambdaQueryWrapper<KnowledgeItem> wrapper = new LambdaQueryWrapper<>();
    if (regionId != null) {
      wrapper.eq(KnowledgeItem::getRegionId, regionId);
    }
    if (industryId != null) {
      wrapper.eq(KnowledgeItem::getIndustryId, industryId);
    }
    wrapper.orderByAsc(KnowledgeItem::getId);
    return knowledgeMapper.selectList(wrapper);
  }

  private KnowledgeItem find(long id) {
    KnowledgeItem item = knowledgeMapper.selectById(id);
    if (item == null) {
      throw new IllegalArgumentException("knowledge item not found");
    }
    return item;
  }
}
