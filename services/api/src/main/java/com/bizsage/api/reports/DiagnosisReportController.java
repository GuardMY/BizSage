package com.bizsage.api.reports;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class DiagnosisReportController {
  private final DiagnosisReportService reportService;
  private final UserStore userStore;

  public DiagnosisReportController(DiagnosisReportService reportService, UserStore userStore) {
    this.reportService = reportService;
    this.userStore = userStore;
  }

  @GetMapping("/diagnosis")
  ApiResponse<DiagnosisReport> diagnosis(
      @RequestParam(defaultValue = "operating diagnosis") String question,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(reportService.build(question, currentUser(principal)), requestId(request));
  }

  private UserAccount currentUser(Principal principal) {
    return userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found"));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
