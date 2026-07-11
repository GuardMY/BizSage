package com.bizsage.api.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bizsage.api.users.UserAccount;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaidIntelligenceStore {
  private final PaidIntelligenceMapper paidIntelligenceMapper;

  public PaidIntelligenceStore(PaidIntelligenceMapper paidIntelligenceMapper) {
    this.paidIntelligenceMapper = paidIntelligenceMapper;
  }

  public List<PaidIntelligenceItem> listFor(UserAccount user) {
    if (!"SEED_PAID".equals(user.membershipLevel())) {
      return List.of();
    }
    return paidIntelligenceMapper.selectList(new LambdaQueryWrapper<PaidIntelligenceItem>()
        .eq(PaidIntelligenceItem::getStatus, "APPROVED")
        .eq(PaidIntelligenceItem::getRegionId, user.regionId())
        .eq(PaidIntelligenceItem::getIndustryId, user.industryId())
        .orderByAsc(PaidIntelligenceItem::getId));
  }

}
