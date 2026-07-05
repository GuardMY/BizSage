package com.bizsage.api.reports;

import com.bizsage.api.intelligence.PaidIntelligenceStore;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.DiagnoseRequest;
import com.bizsage.api.worker.DiagnoseResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisReportService {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisReportService.class);

  private final AiWorkerClient aiWorkerClient;
  private final PaidIntelligenceStore paidIntelligenceStore;

  public DiagnosisReportService(
      AiWorkerClient aiWorkerClient,
      PaidIntelligenceStore paidIntelligenceStore) {
    this.aiWorkerClient = aiWorkerClient;
    this.paidIntelligenceStore = paidIntelligenceStore;
  }

  /**
   * Build a diagnosis report by delegating to the AI worker.
   *
   * <p>The worker's {@code answer} field is mapped to the report's
   * {@code summary} field.  Paid intelligence sources are appended
   * after the worker's knowledge sources.
   *
   * @throws AiWorkerException if the worker is unavailable or LLM is not configured
   */
  public DiagnosisReport build(String question, UserAccount user) {
    // 1. Assemble a minimal request for the worker
    DiagnoseRequest request = DiagnoseRequest.builder()
        .question(question)
        .regionId(user.regionId())
        .industryId(user.industryId())
        .membershipLevel(user.membershipLevel())
        .build();

    // 2. Call the worker — strict failure, no local fallback
    DiagnoseResponse response;
    try {
      response = aiWorkerClient.diagnose(request);
    } catch (AiWorkerException ex) {
      log.error("Report generation failed for user {}: {}", user.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "Report generation failed: worker selfCheckStatus=" + response.selfCheckStatus());
    }

    // 3. Build sources list from worker response
    List<ReportSource> sources = new ArrayList<>();
    if (response.sources() != null) {
      for (DiagnoseResponse.DiagnoseSource src : response.sources()) {
        sources.add(new ReportSource(
            src.id(),
            src.title(),
            src.sourceUrl(),
            src.sourceId(),
            src.confidence(),
            src.entitlement() != null ? src.entitlement() : "FREE"));
      }
    }

    // 4. Append paid intelligence sources
    paidIntelligenceStore.listFor(user).stream()
        .filter(item -> item.status().equals("APPROVED"))
        .map(item -> new ReportSource(
            "paid-" + item.id(),
            item.title(),
            item.url(),
            item.sourceId(),
            item.confidence(),
            item.entitlement()))
        .forEach(sources::add);

    // 5. Worker's "answer" → report's "summary" (stable mapping)
    return new DiagnosisReport(
        "PDF",
        question,
        response.answer(),
        List.copyOf(sources),
        response.confidence(),
        response.timeliness(),
        response.selfCheckStatus(),
        response.disclaimer());
  }
}
