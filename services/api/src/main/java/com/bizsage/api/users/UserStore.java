package com.bizsage.api.users;

import com.bizsage.api.auth.Role;
import com.bizsage.api.privacy.PrivacyService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserStore {
  private final PrivacyService privacyService;
  private final List<UserAccount> users = List.of(
      new UserAccount(1, "admin", "password", Role.SUPER_ADMIN, "13812345678", "110101199003071234",
          "cn-default", "general", "INTERNAL", "operations"),
      new UserAccount(2, "operator", "password", Role.OPERATOR, "13912345678", "110101199003071235",
          "cn-default", "general", "INTERNAL", "review,alerts"),
      new UserAccount(3, "user", "password", Role.USER, "13712345678", "110101199003071236",
          "cn-default", "general", "FREE", "cashflow"),
      new UserAccount(4, "seed_paid", "password", Role.USER, "13612345678", "110101199003071237",
          "cn-default", "general", "SEED_PAID", "cashflow,inventory"));

  public UserStore(PrivacyService privacyService) {
    this.privacyService = privacyService;
  }

  public Optional<UserAccount> findByUsername(String username) {
    return users.stream().filter(user -> user.username().equals(username)).findFirst();
  }

  public List<UserView> listViews() {
    return users.stream().map(this::toView).toList();
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
