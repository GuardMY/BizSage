package com.bizsage.api.reports;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import com.bizsage.api.worker.AiWorkerException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/reports")
public class DiagnosisReportController {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisReportController.class);

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
    return ApiResponse.ok(
        reportService.build(question, currentUser(principal)),
        requestId(request));
  }

  private UserAccount currentUser(Principal principal) {
    return userStore.findByUsername(principal.getName())
        .orElseThrow(() -> new IllegalArgumentException("user not found"));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }

  @RestControllerAdvice(assignableTypes = DiagnosisReportController.class)
  static class ReportExceptionHandler {
    @ExceptionHandler(AiWorkerException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ApiResponse<Void> onWorkerError(AiWorkerException ex, HttpServletRequest request) {
      log.error("Report worker error: {}", ex.getMessage());
      return ApiResponse.error(
          "WORKER_ERROR",
          ex.getMessage() != null ? ex.getMessage() : "AI worker unavailable",
          request.getAttribute(RequestIds.ATTRIBUTE).toString());
    }
  }
}
