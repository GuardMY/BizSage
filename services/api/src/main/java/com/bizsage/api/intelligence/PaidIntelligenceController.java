package com.bizsage.api.intelligence;

import com.bizsage.api.cache.CacheMetrics;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.grayrelease.GrayReleaseService;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paid-intelligence")
public class PaidIntelligenceController {
  private final PaidIntelligenceStore store;
  private final UserStore userStore;
  private final CacheMetrics cacheMetrics;
  private final GrayReleaseService grayRelease;

  public PaidIntelligenceController(PaidIntelligenceStore store, UserStore userStore,
      CacheMetrics cacheMetrics, GrayReleaseService grayRelease) {
    this.store = store;
    this.userStore = userStore;
    this.cacheMetrics = cacheMetrics;
    this.grayRelease = grayRelease;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
  @CacheEvict(value = "dimensionIntel", allEntries = true)
  ApiResponse<PaidIntelligenceItem> create(@Valid @RequestBody CreateIntelligenceRequest body, HttpServletRequest request) {
    cacheMetrics.recordMiss("dimensionIntel");
    return ApiResponse.ok(store.create(body), requestId(request));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
  @CacheEvict(value = "dimensionIntel", allEntries = true)
  ApiResponse<PaidIntelligenceItem> approve(@PathVariable long id, HttpServletRequest request) {
    cacheMetrics.recordMiss("dimensionIntel");
    return ApiResponse.ok(store.approve(id), requestId(request));
  }

  @GetMapping
  @Cacheable(value = "dimensionIntel", key = "#principal.name")
  ApiResponse<List<PaidIntelligenceItem>> list(Principal principal, HttpServletRequest request) {
    cacheMetrics.recordHit("dimensionIntel");
    UserAccount user = currentUser(principal);
    if (!grayRelease.isFeatureEnabled(GrayReleaseService.FEATURE_PAID_INTELLIGENCE, user)) {
      return ApiResponse.ok(List.of(), requestId(request));
    }
    return ApiResponse.ok(store.listFor(user), requestId(request));
  }

  private UserAccount currentUser(Principal principal) {
    return userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found"));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
