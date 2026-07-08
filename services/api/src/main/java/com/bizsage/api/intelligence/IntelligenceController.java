package com.bizsage.api.intelligence;

import com.bizsage.api.cache.CacheMetrics;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.PagedResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.governance.DataIsolationService;
import com.bizsage.api.governance.DataScope;
import com.bizsage.api.worker.KnowledgeSyncInitializer;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intelligence")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class IntelligenceController {
  private final IntelligenceStore store;
  private final CacheMetrics cacheMetrics;
  private final DataIsolationService isolationService;
  private final KnowledgeSyncInitializer syncInitializer;

  public IntelligenceController(IntelligenceStore store, CacheMetrics cacheMetrics,
                                DataIsolationService isolationService,
                                KnowledgeSyncInitializer syncInitializer) {
    this.store = store;
    this.cacheMetrics = cacheMetrics;
    this.isolationService = isolationService;
    this.syncInitializer = syncInitializer;
  }

  @PostMapping
  @CacheEvict(value = "dimensionIntel", allEntries = true)
  ApiResponse<IntelligenceItem> create(@Valid @RequestBody CreateIntelligenceRequest body,
                                       HttpServletRequest request) {
    cacheMetrics.recordMiss("dimensionIntel");
    return ApiResponse.ok(store.create(body), requestId(request));
  }

  /** V2: Approve intelligence AND sync it to the live RAG knowledge base. */
  @PostMapping("/{id}/approve")
  @CacheEvict(value = {"dimensionIntel", "globalKnowledge"}, allEntries = true)
  ApiResponse<IntelligenceItem> approve(@PathVariable long id, HttpServletRequest request) {
    cacheMetrics.recordMiss("dimensionIntel");
    IntelligenceItem approved = store.approve(id);

    // V2: Real-time knowledge base — index approved intelligence into the vector store
    try {
      syncInitializer.syncSingle(approved);
    } catch (Exception e) {
      // Non-fatal: intelligence is approved; indexing can be retried later
      cacheMetrics.recordMiss("globalKnowledge");
    }

    return ApiResponse.ok(approved, requestId(request));
  }

  /** V2: Paginated intelligence list with data-scope filtering.
   *  Admins see all; the filter layers are applied for audit visibility. */
  @GetMapping
  @Cacheable(value = "dimensionIntel", key = "'intel-page-' + #page + '-' + #size")
  ApiResponse<PagedResponse<IntelligenceItem>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal,
      HttpServletRequest request) {
    cacheMetrics.recordHit("dimensionIntel");

    DataScope scope = isolationService.resolveScope(principal);
    if (scope.isBlocked()) {
      return ApiResponse.error("FORBIDDEN", "account is frozen", requestId(request));
    }

    List<IntelligenceItem> all = store.list();
    // Apply data-scope filters: region, industry, paid/free
    List<IntelligenceItem> filtered = all.stream()
        .filter(item -> scope.includesRegion(item.regionId()))
        .filter(item -> scope.includesIndustry(item.industryId()))
        .filter(item -> scope.canAccessPaid() || !"PAID".equals(item.entitlement()))
        .toList();

    int start = (page - 1) * size;
    int end = Math.min(start + size, filtered.size());
    List<IntelligenceItem> pageItems = start < filtered.size() ? filtered.subList(start, end) : List.of();
    return ApiResponse.ok(PagedResponse.of(pageItems, page, size, filtered.size()), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
