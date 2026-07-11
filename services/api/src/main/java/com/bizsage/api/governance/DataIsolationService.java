package com.bizsage.api.governance;

import com.bizsage.api.auth.Role;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import java.security.Principal;
import org.springframework.stereotype.Service;

/** Resolves the effective data scope for each authenticated request. */
@Service
public class DataIsolationService {

  private final UserStore userStore;

  public DataIsolationService(UserStore userStore) {
    this.userStore = userStore;
  }

  public DataScope resolveScope(Principal principal) {
    if (principal == null) {
      return DataScope.unrestricted();
    }
    UserAccount user = userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found: " + principal.getName()));

    if (user.role() == Role.LEGAL_FREEZE) {
      return DataScope.blocked();
    }

    return new DataScope(
        user.regionId(),
        user.industryId(),
        user.membershipLevel(),
        false);
  }
}
