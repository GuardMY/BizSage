package com.bizsage.api.reports;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.grayrelease.GrayReleaseService;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserStore;
import com.bizsage.api.worker.AiWorkerException;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/reports")
public class DiagnosisReportController {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisReportController.class);

  private final DiagnosisReportService reportService;
  private final PdfReportGenerator pdfGenerator;
  private final UserStore userStore;
  private final GrayReleaseService grayRelease;

  public DiagnosisReportController(DiagnosisReportService reportService,
      PdfReportGenerator pdfGenerator, UserStore userStore,
      GrayReleaseService grayRelease) {
    this.reportService = reportService;
    this.pdfGenerator = pdfGenerator;
    this.userStore = userStore;
    this.grayRelease = grayRelease;
  }

  @GetMapping("/diagnosis")
  ApiResponse<DiagnosisReport> diagnosis(
      @RequestParam(defaultValue = "operating diagnosis") String question,
      @RequestParam(required = false) Long conversationId,
      Principal principal,
      HttpServletRequest request) {
    return ApiResponse.ok(
        conversationId != null
            ? reportService.buildForConversation(conversationId, currentUser(principal))
            : reportService.build(question, currentUser(principal)),
        requestId(request));
  }

  /** V2: Returns a formatted PDF binary for the diagnosis report (gray-release gated). */
  @GetMapping("/diagnosis/pdf")
  ResponseEntity<byte[]> diagnosisPdf(
      @RequestParam(defaultValue = "operating diagnosis") String question,
      @RequestParam(required = false) Long conversationId,
      Principal principal,
      HttpServletRequest request) {
    UserAccount user = currentUser(principal);
    if (!grayRelease.isFeatureEnabled(GrayReleaseService.FEATURE_PDF_EXPORT, user)) {
      throw new AiWorkerException("PDF export is not available for your account tier");
    }
    byte[] pdfBytes = conversationId != null
        ? pdfGenerator.generate(reportService.buildForConversation(conversationId, user))
        : reportService.buildPdf(question, user, pdfGenerator);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename("BizSage-Diagnosis-Report.pdf")
        .build());
    headers.set("X-Request-Id", requestId(request));
    return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
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
