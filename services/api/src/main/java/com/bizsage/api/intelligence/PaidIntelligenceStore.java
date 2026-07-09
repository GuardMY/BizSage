package com.bizsage.api.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bizsage.api.users.UserAccount;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaidIntelligenceStore {
  private final PaidIntelligenceMapper paidIntelligenceMapper;

  public PaidIntelligenceStore(PaidIntelligenceMapper paidIntelligenceMapper) {
    this.paidIntelligenceMapper = paidIntelligenceMapper;
  }

  public PaidIntelligenceItem create(CreateIntelligenceRequest request) {
    PaidIntelligenceItem item = new PaidIntelligenceItem();
    item.setTitle(request.title());
    item.setContent(request.content());
    item.setUrl(request.url());
    item.setStatus("PENDING");
    item.setConfidence(0.75D);
    item.setEntitlement("PAID");
    item.setLinkId(request.linkId());
    item.setRegionId(request.regionId());
    item.setIndustryId(request.industryId());
    item.setSourceId(request.sourceId());
    item.setWeight(0.9D);
    item.setContentHash(IntelligenceStore.sha256(request.content()));
    paidIntelligenceMapper.insert(item);
    return find(item.id());
  }

  public PaidIntelligenceItem approve(long id) {
    int updated = paidIntelligenceMapper.update(null, new LambdaUpdateWrapper<PaidIntelligenceItem>()
        .eq(PaidIntelligenceItem::getId, id)
        .set(PaidIntelligenceItem::getStatus, "APPROVED"));
    if (updated == 0) {
      throw new IllegalArgumentException("paid intelligence not found");
    }
    return find(id);
  }

  public List<PaidIntelligenceItem> listFor(UserAccount user) {
    if (user.role().name().equals("SUPER_ADMIN") || user.role().name().equals("OPERATOR")) {
      return paidIntelligenceMapper.selectList(new LambdaQueryWrapper<PaidIntelligenceItem>()
          .orderByAsc(PaidIntelligenceItem::getId));
    }
    if (!"SEED_PAID".equals(user.membershipLevel())) {
      return List.of();
    }
    return paidIntelligenceMapper.selectList(new LambdaQueryWrapper<PaidIntelligenceItem>()
        .eq(PaidIntelligenceItem::getStatus, "APPROVED")
        .eq(PaidIntelligenceItem::getRegionId, user.regionId())
        .eq(PaidIntelligenceItem::getIndustryId, user.industryId())
        .orderByAsc(PaidIntelligenceItem::getId));
  }

  private PaidIntelligenceItem find(long id) {
    PaidIntelligenceItem item = paidIntelligenceMapper.selectById(id);
    if (item == null) {
      throw new IllegalArgumentException("paid intelligence not found");
    }
    return item;
  }
}
