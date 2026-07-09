package com.bizsage.api.users;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        account.consultationPreferences());
  }
}
