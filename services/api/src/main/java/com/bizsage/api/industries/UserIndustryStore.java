package com.bizsage.api.industries;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserIndustryStore {
  private final UserIndustryMapper mapper;

  public UserIndustryStore(UserIndustryMapper mapper) {
    this.mapper = mapper;
  }

  public List<UserIndustry> list(long userId) {
    return mapper.selectList(new LambdaQueryWrapper<UserIndustry>()
        .eq(UserIndustry::getUserId, userId)
        .eq(UserIndustry::getStatus, "ACTIVE")
        .orderByAsc(UserIndustry::getIndustryName));
  }

  public UserIndustry add(long userId, String industryId, String industryName, String sourceType) {
    UserIndustry existing = mapper.selectOne(new LambdaQueryWrapper<UserIndustry>()
        .eq(UserIndustry::getUserId, userId)
        .eq(UserIndustry::getIndustryId, industryId)
        .last("limit 1"));
    if (existing != null) return existing;
    UserIndustry item = new UserIndustry();
    item.setUserId(userId);
    item.setIndustryId(industryId);
    item.setIndustryName(industryName);
    item.setSourceType(sourceType);
    item.setStatus("ACTIVE");
    mapper.insert(item);
    return item;
  }

  public boolean belongsTo(long userId, String industryId) {
    return mapper.selectCount(new LambdaQueryWrapper<UserIndustry>()
        .eq(UserIndustry::getUserId, userId)
        .eq(UserIndustry::getIndustryId, industryId)
        .eq(UserIndustry::getStatus, "ACTIVE")) > 0;
  }
}
