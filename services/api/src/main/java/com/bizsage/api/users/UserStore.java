package com.bizsage.api.users;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bizsage.api.auth.Role;
import com.bizsage.api.privacy.PrivacyService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserStore {
  private final PrivacyService privacyService;
  private final UserMapper userMapper;

  public UserStore(PrivacyService privacyService, UserMapper userMapper) {
    this.privacyService = privacyService;
    this.userMapper = userMapper;
  }

  public Optional<UserAccount> findByUsername(String username) {
    return Optional.ofNullable(userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
        .eq(UserAccount::getUsername, username)
        .last("limit 1")));
  }

  public List<UserView> listViews() {
    return userMapper.selectList(new LambdaQueryWrapper<UserAccount>()
        .orderByAsc(UserAccount::getId)).stream().map(this::toView).toList();
  }

  public UserView updatePreferredLocale(String username, String preferredLocale) {
    int updated = userMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
        .eq(UserAccount::getUsername, username)
        .set(UserAccount::getPreferredLocale, preferredLocale));
    if (updated == 0) {
      throw new IllegalArgumentException("user not found");
    }
    return findByUsername(username)
        .map(this::toView)
        .orElseThrow(() -> new IllegalArgumentException("user not found"));
  }

  public UserView toView(UserAccount account) {
    return new UserView(
        account.id(),
        account.username(),
        account.role(),
        privacyService.maskPhone(account.phone()),
        privacyService.maskIdentity(account.identity()),
        account.regionId(),
        account.industryId(),
        account.membershipLevel(),
        account.consultationPreferences(),
        account.preferredLocale());
  }
}
