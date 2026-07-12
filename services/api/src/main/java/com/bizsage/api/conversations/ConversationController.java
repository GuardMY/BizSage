package com.bizsage.api.conversations;

import com.bizsage.api.cache.CacheMetrics;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.PagedResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.governance.DataIsolationService;
import com.bizsage.api.governance.DataScope;
import com.bizsage.api.industries.UserIndustryStore;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
  private final ConversationStore conversationStore;
  private final UserStore userStore;
  private final CacheMetrics cacheMetrics;
  private final DataIsolationService isolationService;
  private final UserIndustryStore industryStore;

  public ConversationController(ConversationStore conversationStore, UserStore userStore,
                                CacheMetrics cacheMetrics, DataIsolationService isolationService,
                                UserIndustryStore industryStore) {
    this.conversationStore = conversationStore;
    this.userStore = userStore;
    this.cacheMetrics = cacheMetrics;
    this.isolationService = isolationService;
    this.industryStore = industryStore;
  }

  @PostMapping
  @CacheEvict(value = "apiResponses", allEntries = true)
  ApiResponse<Conversation> create(
      @Valid @RequestBody CreateConversationRequest body,
      Principal principal,
      HttpServletRequest request) {
    cacheMetrics.recordMiss("apiResponses");
    DataScope scope = isolationService.resolveScope(principal);
    if (scope.isBlocked()) {
      return ApiResponse.error("FORBIDDEN", "account is frozen", requestId(request));
    }
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    String industryId = body.industryId() == null || body.industryId().isBlank()
        ? user.industryId() : body.industryId();
    if (!industryStore.belongsTo(user.id(), industryId)) {
      return ApiResponse.error("INVALID_INDUSTRY", "industry is not configured for this user", requestId(request));
    }
    Conversation created = conversationStore.create(user.username(), body.title(),
        user.regionId(), industryId);
    return ApiResponse.ok(created, requestId(request));
  }

  /** V2: Paginated conversation list with data-scope-aware filtering. */
  @GetMapping
  @Cacheable(value = "apiResponses", key = "#principal.name + '-page-' + #page + '-' + #size")
  ApiResponse<PagedResponse<Conversation>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal,
      HttpServletRequest request) {
    cacheMetrics.recordHit("apiResponses");

    DataScope scope = isolationService.resolveScope(principal);
    if (scope.isBlocked()) {
      return ApiResponse.error("FORBIDDEN", "account is frozen", requestId(request));
    }

    List<Conversation> all = conversationStore.listFor(principal.getName());
    List<Conversation> filtered = all.stream()
        .filter(c -> scope.includesRegion(c.regionId()))
        .filter(c -> scope.includesIndustry(c.industryId()))
        .toList();

    int start = (page - 1) * size;
    int end = Math.min(start + size, filtered.size());
    List<Conversation> pageItems = start < filtered.size() ? filtered.subList(start, end) : List.of();
    return ApiResponse.ok(PagedResponse.of(pageItems, page, size, filtered.size()), requestId(request));
  }

  @PostMapping("/{id}/archive")
  @CacheEvict(value = "apiResponses", allEntries = true)
  ApiResponse<Conversation> archive(@PathVariable long id, Principal principal, HttpServletRequest request) {
    cacheMetrics.recordMiss("apiResponses");
    return ApiResponse.ok(conversationStore.archive(principal.getName(), id), requestId(request));
  }

  @PostMapping("/{id}/delete")
  @CacheEvict(value = "apiResponses", allEntries = true)
  ApiResponse<Conversation> delete(@PathVariable long id, Principal principal, HttpServletRequest request) {
    cacheMetrics.recordMiss("apiResponses");
    return ApiResponse.ok(conversationStore.softDelete(principal.getName(), id), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }

  record CreateConversationRequest(@NotBlank String title, String industryId) {
  }
}
