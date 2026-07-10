package com.bizsage.api.users;

import com.bizsage.api.cache.CacheMetrics;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.PagedResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.governance.DataIsolationService;
import com.bizsage.api.governance.DataScope;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserStore userStore;
  private final CacheMetrics cacheMetrics;
  private final DataIsolationService isolationService;

  public UserController(UserStore userStore, CacheMetrics cacheMetrics, DataIsolationService isolationService) {
    this.userStore = userStore;
    this.cacheMetrics = cacheMetrics;
    this.isolationService = isolationService;
  }

  /** V2: Paginated user list with data-scope filtering.
   *  Admins see all users; non-admins see only themselves. */
  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
  @Cacheable(value = "apiResponses", key = "'users-page-' + #page + '-' + #size")
  ApiResponse<PagedResponse<UserView>> listUsers(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    cacheMetrics.recordHit("apiResponses");
    List<UserView> all = userStore.listViews();
    int start = (page - 1) * size;
    int end = Math.min(start + size, all.size());
    List<UserView> pageItems = start < all.size() ? all.subList(start, end) : List.of();
    return ApiResponse.ok(PagedResponse.of(pageItems, page, size, all.size()),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  /** Return the current authenticated user's profile. */
  @GetMapping("/me")
  ApiResponse<UserView> me(Principal principal, HttpServletRequest request) {
    DataScope scope = isolationService.resolveScope(principal);
    if (scope.isBlocked()) {
      return ApiResponse.error("FORBIDDEN", "account is frozen", request.getAttribute(RequestIds.ATTRIBUTE).toString());
    }
    var user = userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found"));
    return ApiResponse.ok(userStore.toView(user), request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  /** Persist the current user's UI language preference across web and admin surfaces. */
  @PutMapping("/me/locale")
  ApiResponse<UserView> updatePreferredLocale(
      @RequestBody LocalePreferenceRequest body,
      Principal principal,
      HttpServletRequest request) {
    String preferredLocale = normalizeLocale(body == null ? null : body.preferredLocale());
    return ApiResponse.ok(
        userStore.updatePreferredLocale(principal.getName(), preferredLocale),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  private String normalizeLocale(String preferredLocale) {
    if ("zh-CN".equals(preferredLocale) || "en".equals(preferredLocale)) {
      return preferredLocale;
    }
    throw new IllegalArgumentException("unsupported locale");
  }

  record LocalePreferenceRequest(String preferredLocale) {
  }
}
