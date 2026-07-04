package com.bizsage.api.intelligence;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
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

  public PaidIntelligenceController(PaidIntelligenceStore store, UserStore userStore) {
    this.store = store;
    this.userStore = userStore;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
  ApiResponse<PaidIntelligenceItem> create(@Valid @RequestBody CreateIntelligenceRequest body, HttpServletRequest request) {
    return ApiResponse.ok(store.create(body), requestId(request));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
  ApiResponse<PaidIntelligenceItem> approve(@PathVariable long id, HttpServletRequest request) {
    return ApiResponse.ok(store.approve(id), requestId(request));
  }

  @GetMapping
  ApiResponse<List<PaidIntelligenceItem>> list(Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.listFor(currentUser(principal)), requestId(request));
  }

  private UserAccount currentUser(Principal principal) {
    return userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found"));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
