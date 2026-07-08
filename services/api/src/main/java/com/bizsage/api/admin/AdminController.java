package com.bizsage.api.admin;

import com.bizsage.api.admin.AdminDtos.AdminList;
import com.bizsage.api.admin.AdminDtos.AlertActionRequest;
import com.bizsage.api.admin.AdminDtos.AlertItem;
import com.bizsage.api.admin.AdminDtos.AuditLogItem;
import com.bizsage.api.admin.AdminDtos.Dashboard;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceCreateRequest;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceItem;
import com.bizsage.api.admin.AdminDtos.HumanIntelligenceReviewRequest;
import com.bizsage.api.admin.AdminDtos.IntelligenceReviewItem;
import com.bizsage.api.admin.AdminDtos.ReviewVerdictRequest;
import com.bizsage.api.admin.AdminDtos.TicketItem;
import com.bizsage.api.admin.AdminDtos.TicketTransitionRequest;
import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
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

  public AdminController(AdminStore store) {
    this.store = store;
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
  ApiResponse<AlertItem> alertAction(
      @PathVariable long id,
      @PathVariable String action,
      @RequestBody(required = false) AlertActionRequest body,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(store.updateAlert(id, action, actor(principal), body == null ? "" : body.notes()), requestId(request));
  }

  @GetMapping("/audit-logs")
  ApiResponse<AdminList<AuditLogItem>> auditLogs(@RequestParam(required = false) String q, HttpServletRequest request) {
    return ApiResponse.ok(store.listAuditLogs(q), requestId(request));
  }

  @GetMapping("/intelligence-reviews")
  ApiResponse<AdminList<IntelligenceReviewItem>> intelligenceReviews(
      @RequestParam(required = false) String status,
      HttpServletRequest request) {
    return ApiResponse.ok(store.listReviews(status), requestId(request));
  }

  @PostMapping("/intelligence-reviews/{id}/verdict")
  ApiResponse<IntelligenceReviewItem> reviewVerdict(
      @PathVariable long id,
      @RequestBody ReviewVerdictRequest body,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(store.decideReview(id, body.verdict(), body.notes(), actor(principal)), requestId(request));
  }

  @GetMapping("/tickets")
  ApiResponse<AdminList<TicketItem>> tickets(@RequestParam(required = false) String status, HttpServletRequest request) {
    return ApiResponse.ok(store.listTickets(status), requestId(request));
  }

  @PostMapping("/tickets/{id}/transition")
  ApiResponse<TicketItem> transitionTicket(
      @PathVariable long id,
      @RequestBody TicketTransitionRequest body,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(store.transitionTicket(id, body.status(), body.owner(), body.nextAction(), actor(principal), body.notes()), requestId(request));
  }

  @GetMapping("/human-intelligence")
  ApiResponse<AdminList<HumanIntelligenceItem>> humanIntelligence(
      @RequestParam(required = false) String status,
      HttpServletRequest request) {
    return ApiResponse.ok(store.listHumanIntelligence(status), requestId(request));
  }

  @PostMapping("/human-intelligence")
  ApiResponse<HumanIntelligenceItem> createHumanIntelligence(
      @RequestBody HumanIntelligenceCreateRequest body,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(store.createHumanIntelligence(body, actor(principal)), requestId(request));
  }

  @PostMapping("/human-intelligence/{id}/review")
  ApiResponse<HumanIntelligenceItem> reviewHumanIntelligence(
      @PathVariable long id,
      @RequestBody HumanIntelligenceReviewRequest body,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(store.reviewHumanIntelligence(id, body.verdict(), body.notes(), actor(principal)), requestId(request));
  }

  private String actor(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
