package com.bizsage.api.governance;

import com.bizsage.api.auth.Role;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.stereotype.Service;

/**
 * Resolves the four-layer data isolation scope for each authenticated request.
 *
 * <p>Layers:
 * <ol>
 *   <li><b>User-private</b> — scope by user identity; private data is encrypted separately.</li>
 *   <li><b>Regional</b> — restrict intelligence/knowledge queries to the user's region.</li>
 *   <li><b>Industry</b> — restrict intelligence/knowledge queries to the user's industry.</li>
 *   <li><b>Paid/free</b> — filter out PAID content for FREE-tier users.</li>
 * </ol>
 */
@Service
public class DataIsolationService {

  private final UserStore userStore;

  public DataIsolationService(UserStore userStore) {
    this.userStore = userStore;
  }

  /**
   * Resolve the effective data scope for the authenticated principal.
   *
   * <ul>
   *   <li>SUPER_ADMIN / OPERATOR → unrestricted (null region, null industry), audit log flag set.</li>
   *   <li>SEED_PAID → scoped to the user's own region/industry, can access paid content.</li>
   *   <li>FREE → scoped to the user's own region/industry, FREE content only.</li>
   *   <li>No principal → system context, unrestricted.</li>
   * </ul>
   */
  public DataScope resolveScope(Principal principal) {
    if (principal == null) {
      return DataScope.unrestricted();
    }
    UserAccount user = userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found: " + principal.getName()));

    Role role = user.role();
    boolean isAdmin = role == Role.SUPER_ADMIN || role == Role.OPERATOR;

    if (isAdmin) {
      return new DataScope(null, null, user.membershipLevel(), true);
    }

    return new DataScope(
        user.regionId(),
        user.industryId(),
        user.membershipLevel(),
        false);
  }
}
