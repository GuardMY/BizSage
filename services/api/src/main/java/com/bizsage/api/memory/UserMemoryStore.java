package com.bizsage.api.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserMemoryStore {
  private final UserMemoryMapper userMemoryMapper;

  public UserMemoryStore(UserMemoryMapper userMemoryMapper) {
    this.userMemoryMapper = userMemoryMapper;
  }

  public List<UserMemoryProfile> activeMemoriesForUser(long userId) {
    return userMemoryMapper.selectList(new LambdaQueryWrapper<UserMemoryProfile>()
        .eq(UserMemoryProfile::getUserId, userId)
        .eq(UserMemoryProfile::getStatus, "ACTIVE")
        .and(w -> w.isNull(UserMemoryProfile::getExpiresAt).or().gt(UserMemoryProfile::getExpiresAt, Instant.now()))
        .orderByDesc(UserMemoryProfile::getConfidence)
        .orderByAsc(UserMemoryProfile::getId));
  }

  public void markUsed(List<Long> memoryIds) {
    if (memoryIds.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    memoryIds.forEach(id -> userMemoryMapper.update(null, new LambdaUpdateWrapper<UserMemoryProfile>()
        .eq(UserMemoryProfile::getId, id)
        .set(UserMemoryProfile::getLastUsedAt, now)));
  }

  public UserMemoryProfile saveOrRefresh(
      long userId,
      String category,
      String key,
      String value,
      String valueType,
      double confidence,
      long sourceConversationId,
      long sourceMessageId,
      boolean structured) {
    UserMemoryProfile existing = userMemoryMapper.selectOne(new LambdaQueryWrapper<UserMemoryProfile>()
        .eq(UserMemoryProfile::getUserId, userId)
        .eq(UserMemoryProfile::getCategory, category)
        .eq(UserMemoryProfile::getKey, key)
        .eq(UserMemoryProfile::getStatus, "ACTIVE")
        .last("limit 1"));
    Instant expiresAt = expiryFor(category);
    if (existing != null) {
      userMemoryMapper.update(null, new LambdaUpdateWrapper<UserMemoryProfile>()
          .eq(UserMemoryProfile::getId, existing.getId())
          .set(UserMemoryProfile::getValue, value)
          .set(UserMemoryProfile::getValueType, valueType)
          .set(UserMemoryProfile::getConfidence, confidence)
          .set(UserMemoryProfile::getSourceConversationId, sourceConversationId)
          .set(UserMemoryProfile::getSourceMessageId, sourceMessageId)
          .set(UserMemoryProfile::getExpiresAt, expiresAt));
      return get(existing.getId());
    }

    UserMemoryProfile profile = new UserMemoryProfile();
    profile.setUserId(userId);
    profile.setCategory(category);
    profile.setKey(key);
    profile.setValue(value);
    profile.setValueType(valueType);
    profile.setConfidence(confidence);
    profile.setSourceConversationId(sourceConversationId);
    profile.setSourceMessageId(sourceMessageId);
    profile.setExpiresAt(expiresAt);
    profile.setStatus("ACTIVE");
    userMemoryMapper.insert(profile);
    return get(profile.getId());
  }

  private UserMemoryProfile get(long id) {
    UserMemoryProfile profile = userMemoryMapper.selectById(id);
    if (profile == null) {
      throw new IllegalArgumentException("memory profile not found");
    }
    return profile;
  }

  private Instant expiryFor(String category) {
    return switch (category) {
      case "BUSINESS_FACT" -> Instant.now().plus(90, ChronoUnit.DAYS);
      case "PREFERENCE" -> Instant.now().plus(180, ChronoUnit.DAYS);
      case "PAIN_POINT" -> Instant.now().plus(90, ChronoUnit.DAYS);
      case "INDUSTRY_CONTEXT" -> Instant.now().plus(365, ChronoUnit.DAYS);
      case "LEARNING_PROGRESS" -> Instant.now().plus(180, ChronoUnit.DAYS);
      default -> null;
    };
  }
}
