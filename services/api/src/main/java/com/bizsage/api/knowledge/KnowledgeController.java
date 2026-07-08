package com.bizsage.api.knowledge;

import com.bizsage.api.cache.CacheMetrics;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.PagedResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.governance.DataIsolationService;
import com.bizsage.api.governance.DataScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class KnowledgeController {
  private final KnowledgeStore store;
  private final CacheMetrics cacheMetrics;
  private final DataIsolationService isolationService;

  public KnowledgeController(KnowledgeStore store, CacheMetrics cacheMetrics,
                             DataIsolationService isolationService) {
    this.store = store;
    this.cacheMetrics = cacheMetrics;
    this.isolationService = isolationService;
  }

  /** V2: Paginated knowledge list with data-scope filtering. */
  @GetMapping
  @Cacheable(value = "globalKnowledge", key = "'knowledge-page-' + #page + '-' + #size")
  ApiResponse<PagedResponse<KnowledgeItem>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal,
      HttpServletRequest request) {
    cacheMetrics.recordHit("globalKnowledge");

    DataScope scope = isolationService.resolveScope(principal);
    if (scope.isBlocked()) {
      return ApiResponse.error("FORBIDDEN", "account is frozen",
          request.getAttribute(RequestIds.ATTRIBUTE).toString());
    }

    List<KnowledgeItem> all = store.list();
    List<KnowledgeItem> filtered = all.stream()
        .filter(item -> scope.includesRegion(item.regionId()))
        .filter(item -> scope.includesIndustry(item.industryId()))
        .filter(item -> scope.canAccessPaid() || !"PAID".equals(item.entitlement()))
        .toList();

    int start = (page - 1) * size;
    int end = Math.min(start + size, filtered.size());
    List<KnowledgeItem> pageItems = start < filtered.size() ? filtered.subList(start, end) : List.of();
    return ApiResponse.ok(PagedResponse.of(pageItems, page, size, filtered.size()),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PostMapping("/import")
  @CacheEvict(value = "globalKnowledge", allEntries = true)
  ApiResponse<KnowledgeItem> importItem(@Valid @RequestBody ImportKnowledgeRequest body,
                                        HttpServletRequest request) {
    cacheMetrics.recordMiss("globalKnowledge");
    return ApiResponse.ok(store.importItem(body),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
