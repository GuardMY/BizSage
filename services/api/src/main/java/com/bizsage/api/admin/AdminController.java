package com.bizsage.api.admin;

import com.bizsage.api.admin.AdminCollectionDtos.CollectionDeadLetter;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionJobRun;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionKeyword;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionKeywordUpsertRequest;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionSourceDetail;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionSourceUpsertRequest;
import com.bizsage.api.admin.AdminDtos.AdminList;
import com.bizsage.api.governance.ConflictStore;
import com.bizsage.api.governance.ConflictStore.ConflictResolutionRecord;
import com.bizsage.api.governance.ConflictStore.FalseLedgerItem;
import com.bizsage.api.admin.AdminDtos.AlertActionRequest;
import com.bizsage.api.admin.AdminDtos.AlertItem;
import com.bizsage.api.admin.AdminDtos.AuditLogItem;
import com.bizsage.api.admin.AdminDtos.Dashboard;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceCreateRequest;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceItem;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceReviewRequest;
import com.bizsage.api.admin.AdminDtos.IntelligenceReviewItem;
import com.bizsage.api.admin.AdminDtos.ReviewVerdictRequest;
import com.bizsage.api.admin.AdminDtos.RiskRule;
import com.bizsage.api.admin.AdminDtos.RiskRuleUpsertRequest;
import com.bizsage.api.admin.AdminDtos.TicketItem;
import com.bizsage.api.admin.AdminDtos.TicketTransitionRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.InspectionReport;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeDraftRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeNodeDetail;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgePublishRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeReviewRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeRollbackRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeTreeNode;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeVersionDiff;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminController {
  private final AdminStore store;
  private final AdminKnowledgeStore knowledgeStore;
  private final AdminCollectionStore collectionStore;
  private final ConflictStore conflictStore;

  public AdminController(AdminStore store, AdminKnowledgeStore knowledgeStore,
      AdminCollectionStore collectionStore, ConflictStore conflictStore) {
    this.store = store;
    this.knowledgeStore = knowledgeStore;
    this.collectionStore = collectionStore;
    this.conflictStore = conflictStore;
  }

  @GetMapping("/dashboard")
  ApiResponse<Dashboard> dashboard(HttpServletRequest request) {
    return ApiResponse.ok(store.dashboard(), requestId(request));
  }

  @GetMapping("/alerts")
  ApiResponse<AdminList<AlertItem>> alerts(@RequestParam(required = false) String status, HttpServletRequest request) {
    return ApiResponse.ok(store.listAlerts(status), requestId(request));
  }

  @PostMapping("/alerts/{id}/{action}")
  ApiResponse<AlertItem> alertAction(@PathVariable long id, @PathVariable String action, @RequestBody(required = false) AlertActionRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.updateAlert(id, action, actor(principal), body == null ? "" : body.notes()), requestId(request));
  }

  @GetMapping("/audit-logs")
  ApiResponse<AdminList<AuditLogItem>> auditLogs(@RequestParam(required = false) String q, HttpServletRequest request) {
    return ApiResponse.ok(store.listAuditLogs(q), requestId(request));
  }

  @GetMapping("/intelligence-reviews")
  ApiResponse<AdminList<IntelligenceReviewItem>> intelligenceReviews(@RequestParam(required = false) String status, HttpServletRequest request) {
    return ApiResponse.ok(store.listReviews(status), requestId(request));
  }

  @PostMapping("/intelligence-reviews/{id}/verdict")
  ApiResponse<IntelligenceReviewItem> reviewVerdict(@PathVariable long id, @RequestBody ReviewVerdictRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.decideReview(id, body.verdict(), body.notes(), actor(principal)), requestId(request));
  }

  @GetMapping("/tickets")
  ApiResponse<AdminList<TicketItem>> tickets(@RequestParam(required = false) String status, HttpServletRequest request) {
    return ApiResponse.ok(store.listTickets(status), requestId(request));
  }

  @PostMapping("/tickets/{id}/transition")
  ApiResponse<TicketItem> transitionTicket(@PathVariable long id, @RequestBody TicketTransitionRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.transitionTicket(id, body.status(), body.owner(), body.nextAction(), actor(principal), body.notes()), requestId(request));
  }

  @GetMapping("/human-intelligence")
  ApiResponse<AdminList<HumanIntelligenceItem>> humanIntelligence(@RequestParam(required = false) String status, HttpServletRequest request) {
    return ApiResponse.ok(store.listHumanIntelligence(status), requestId(request));
  }

  @PostMapping("/human-intelligence")
  ApiResponse<HumanIntelligenceItem> createHumanIntelligence(@RequestBody HumanIntelligenceCreateRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.createHumanIntelligence(body, actor(principal)), requestId(request));
  }

  @PostMapping("/human-intelligence/{id}/review")
  ApiResponse<HumanIntelligenceItem> reviewHumanIntelligence(@PathVariable long id, @RequestBody HumanIntelligenceReviewRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.reviewHumanIntelligence(id, body.verdict(), body.notes(), actor(principal)), requestId(request));
  }

  @GetMapping("/knowledge/nodes")
  ApiResponse<List<KnowledgeTreeNode>> knowledgeNodes(HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.listNodes(), requestId(request));
  }

  @GetMapping("/knowledge/nodes/{nodeId}")
  ApiResponse<KnowledgeNodeDetail> knowledgeNode(@PathVariable long nodeId, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.detail(nodeId), requestId(request));
  }

  @PostMapping("/knowledge/drafts")
  ApiResponse<KnowledgeNodeDetail> saveKnowledgeDraft(@RequestBody KnowledgeDraftRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.saveDraft(body, actor(principal)), requestId(request));
  }

  @PostMapping("/knowledge/nodes/{nodeId}/versions/{versionId}/submit-review")
  ApiResponse<KnowledgeNodeDetail> submitKnowledgeReview(@PathVariable long nodeId, @PathVariable long versionId, @RequestBody(required = false) KnowledgeReviewRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.submitReview(nodeId, versionId, notes(body), actor(principal)), requestId(request));
  }

  @PostMapping("/knowledge/nodes/{nodeId}/versions/{versionId}/approve")
  ApiResponse<KnowledgeNodeDetail> approveKnowledgeReview(@PathVariable long nodeId, @PathVariable long versionId, @RequestBody(required = false) KnowledgeReviewRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.approveReview(nodeId, versionId, notes(body), actor(principal)), requestId(request));
  }

  @PostMapping("/knowledge/nodes/{nodeId}/versions/{versionId}/publish")
  ApiResponse<KnowledgeNodeDetail> publishKnowledgeVersion(@PathVariable long nodeId, @PathVariable long versionId, @RequestBody(required = false) KnowledgePublishRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.publish(nodeId, versionId, body == null ? "" : body.notes(), actor(principal)), requestId(request));
  }

  @PostMapping("/knowledge/nodes/{nodeId}/rollback")
  ApiResponse<KnowledgeNodeDetail> rollbackKnowledgeNode(@PathVariable long nodeId, @RequestBody KnowledgeRollbackRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.rollback(nodeId, body, actor(principal)), requestId(request));
  }

  @GetMapping("/knowledge/diff")
  ApiResponse<KnowledgeVersionDiff> knowledgeDiff(@RequestParam long leftVersionId, @RequestParam long rightVersionId, HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.diff(leftVersionId, rightVersionId), requestId(request));
  }

  @GetMapping("/knowledge/inspect")
  ApiResponse<InspectionReport> knowledgeInspect(HttpServletRequest request) {
    return ApiResponse.ok(knowledgeStore.inspect(), requestId(request));
  }

  @GetMapping("/collection/sources")
  ApiResponse<List<com.bizsage.api.admin.AdminCollectionDtos.CollectionSourceConfig>> collectionSources(HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.listSources(), requestId(request));
  }

  @GetMapping("/collection/sources/{sourceConfigId}")
  ApiResponse<CollectionSourceDetail> collectionSourceDetail(@PathVariable long sourceConfigId, HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.sourceDetail(sourceConfigId), requestId(request));
  }

  @PostMapping("/collection/sources")
  ApiResponse<CollectionSourceDetail> saveCollectionSource(@RequestBody CollectionSourceUpsertRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.saveSource(body, actor(principal)), requestId(request));
  }

  @GetMapping("/collection/keywords")
  ApiResponse<List<CollectionKeyword>> collectionKeywords(@RequestParam(required = false) Long sourceConfigId, HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.listKeywords(sourceConfigId), requestId(request));
  }

  @PostMapping("/collection/keywords")
  ApiResponse<CollectionKeyword> saveCollectionKeyword(@RequestBody CollectionKeywordUpsertRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.saveKeyword(body, actor(principal)), requestId(request));
  }

  @PostMapping("/collection/sources/{sourceConfigId}/run")
  ApiResponse<CollectionJobRun> runCollectionSource(@PathVariable long sourceConfigId, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.runNow(sourceConfigId, actor(principal)), requestId(request));
  }

  @GetMapping("/collection/jobs")
  ApiResponse<List<CollectionJobRun>> collectionJobs(@RequestParam(required = false) String status, HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.listRuns(status), requestId(request));
  }

  @GetMapping("/collection/dead-letters")
  ApiResponse<List<CollectionDeadLetter>> collectionDeadLetters(HttpServletRequest request) {
    return ApiResponse.ok(collectionStore.listDeadLetters(), requestId(request));
  }

  @PostMapping("/collection/sources/{sourceConfigId}/archive")
  ApiResponse<String> archiveCollectionSource(@PathVariable long sourceConfigId, Principal principal, HttpServletRequest request) {
    store.archiveCollectionSource(sourceConfigId, actor(principal));
    return ApiResponse.ok("archived", requestId(request));
  }

  @PostMapping("/collection/sources/{sourceConfigId}/restore")
  ApiResponse<String> restoreCollectionSource(@PathVariable long sourceConfigId, Principal principal, HttpServletRequest request) {
    store.restoreCollectionSource(sourceConfigId, actor(principal));
    return ApiResponse.ok("restored", requestId(request));
  }

  @GetMapping("/risk-rules")
  ApiResponse<List<RiskRule>> riskRules(HttpServletRequest request) {
    return ApiResponse.ok(store.listRiskRules(), requestId(request));
  }

  @PostMapping("/risk-rules")
  ApiResponse<RiskRule> upsertRiskRule(@RequestBody RiskRuleUpsertRequest body, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.upsertRiskRule(body, actor(principal)), requestId(request));
  }

  @PostMapping("/risk-rules/{id}/toggle")
  ApiResponse<RiskRule> toggleRiskRule(@PathVariable long id, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(store.toggleRiskRule(id, actor(principal)), requestId(request));
  }

  @GetMapping("/governance/conflicts")
  ApiResponse<AdminList<ConflictResolutionRecord>> listConflicts(
      @RequestParam(required = false, defaultValue = "cn-default") String region,
      @RequestParam(required = false, defaultValue = "general") String industry,
      @RequestParam(required = false, defaultValue = "50") int limit,
      HttpServletRequest request) {
    var items = conflictStore.listConflicts(region, industry, limit);
    return ApiResponse.ok(new AdminList<>(items, items.size(), null), requestId(request));
  }

  @GetMapping("/governance/false-ledger")
  ApiResponse<AdminList<FalseLedgerItem>> listFalseLedger(
      @RequestParam(required = false, defaultValue = "cn-default") String region,
      @RequestParam(required = false, defaultValue = "general") String industry,
      @RequestParam(required = false, defaultValue = "50") int limit,
      HttpServletRequest request) {
    var items = conflictStore.listFalseLedger(region, industry, limit);
    return ApiResponse.ok(new AdminList<>(items, items.size(), null), requestId(request));
  }

  private String notes(KnowledgeReviewRequest body) {
    return body == null ? "" : body.notes();
  }

  private String actor(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
