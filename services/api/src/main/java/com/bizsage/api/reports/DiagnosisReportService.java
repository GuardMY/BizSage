package com.bizsage.api.reports;

import com.bizsage.api.governance.DataScope;
import com.bizsage.api.intelligence.IntelligenceItem;
import com.bizsage.api.intelligence.IntelligenceStore;
import com.bizsage.api.intelligence.PaidIntelligenceStore;
import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeStore;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.DiagnoseRequest;
import com.bizsage.api.worker.DiagnoseResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisReportService {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisReportService.class);

  private final AiWorkerClient aiWorkerClient;
  private final PaidIntelligenceStore paidIntelligenceStore;
  private final KnowledgeStore knowledgeStore;
  private final IntelligenceStore intelligenceStore;

  public DiagnosisReportService(
      AiWorkerClient aiWorkerClient,
      PaidIntelligenceStore paidIntelligenceStore,
      KnowledgeStore knowledgeStore,
      IntelligenceStore intelligenceStore) {
    this.aiWorkerClient = aiWorkerClient;
    this.paidIntelligenceStore = paidIntelligenceStore;
    this.knowledgeStore = knowledgeStore;
    this.intelligenceStore = intelligenceStore;
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
    // 1. Load knowledge base for the worker
    List<Map<String, Object>> knowledgeItems = loadKnowledgeForReport(
        user.regionId(), user.industryId(), user.membershipLevel());

    // 2. Assemble the request with knowledge
    DiagnoseRequest request = DiagnoseRequest.builder()
        .question(question)
        .knowledge(knowledgeItems)
        .regionId(user.regionId())
        .industryId(user.industryId())
        .membershipLevel(user.membershipLevel())
        .build();

    // 3. Call the worker — strict failure, no local fallback
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

    // 4. Build sources list from worker response
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

    // 5. Append paid intelligence sources
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

    // 6. Worker's "answer" → report's "summary" (stable mapping)
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

  // ── Knowledge loading ───────────────────────────────────────────

  private List<Map<String, Object>> loadKnowledgeForReport(
      String regionId, String industryId, String membershipLevel) {
    DataScope scope = new DataScope(regionId, industryId, membershipLevel, false);

    List<KnowledgeItem> knowledgeItems = knowledgeStore.listScoped(regionId, industryId);
    List<IntelligenceItem> intelligenceItems = intelligenceStore.listApprovedScoped(scope);

    List<Map<String, Object>> combined = new ArrayList<>();
    for (KnowledgeItem item : knowledgeItems) {
      combined.add(knowledgeToMap(item));
    }
    for (IntelligenceItem item : intelligenceItems) {
      combined.add(intelligenceToMap(item));
    }
    return combined;
  }

  private Map<String, Object> knowledgeToMap(KnowledgeItem item) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "kb-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.sourceUrl() != null ? item.sourceUrl() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "knowledge");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
    return map;
  }

  private Map<String, Object> intelligenceToMap(IntelligenceItem item) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "intel-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.url() != null ? item.url() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "intelligence");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
    return map;
  }
}
