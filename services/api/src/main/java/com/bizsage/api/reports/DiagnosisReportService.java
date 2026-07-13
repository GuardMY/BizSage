package com.bizsage.api.reports;

import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeStore;
import com.bizsage.api.conversations.Conversation;
import com.bizsage.api.conversations.ConversationStore;
import com.bizsage.api.messages.ConversationMessage;
import com.bizsage.api.messages.ConversationMessageStore;
import com.bizsage.api.messages.ConversationSummary;
import com.bizsage.api.messages.ConversationSummaryStore;
import com.bizsage.api.memory.ConversationDiagnosisMemoryStore;
import com.bizsage.api.memory.UserMemoryStore;
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
  private final KnowledgeStore knowledgeStore;
  private final ConversationMessageStore messageStore;
  private final ConversationSummaryStore summaryStore;
  private final UserMemoryStore userMemoryStore;
  private final ConversationDiagnosisMemoryStore diagnosisMemoryStore;
  private final ConversationStore conversationStore;

  public DiagnosisReportService(
      AiWorkerClient aiWorkerClient,
      KnowledgeStore knowledgeStore,
      ConversationMessageStore messageStore,
      ConversationSummaryStore summaryStore,
      UserMemoryStore userMemoryStore,
      ConversationDiagnosisMemoryStore diagnosisMemoryStore,
      ConversationStore conversationStore) {
    this.aiWorkerClient = aiWorkerClient;
    this.knowledgeStore = knowledgeStore;
    this.messageStore = messageStore;
    this.summaryStore = summaryStore;
    this.userMemoryStore = userMemoryStore;
    this.diagnosisMemoryStore = diagnosisMemoryStore;
    this.conversationStore = conversationStore;
  }

  public DiagnosisReport buildForConversation(long conversationId, UserAccount user) {
    List<ConversationMessage> messages = messageStore.recentActiveMessages(conversationId, 20);
    Conversation conversation = conversationStore.getForOwner(user.username(), conversationId);
    String question = messages.stream().filter(message -> "USER".equals(message.sender()))
        .reduce((first, second) -> second).map(ConversationMessage::content).orElse("经营诊断");
    ConversationSummary summary = summaryStore.latestActive(conversationId).orElse(null);
    List<Map<String, Object>> recent = messages.stream().map(message -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("role", "USER".equals(message.sender()) ? "user" : "assistant");
      map.put("content", message.content());
      return map;
    }).toList();
    List<Map<String, Object>> profileMemories = userMemoryStore.activeMemoriesForUser(user.id()).stream().map(memory -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("category", memory.category());
      map.put("key", memory.key());
      map.put("value", memory.value());
      map.put("confidence", memory.confidence());
      return map;
    }).toList();
    DiagnoseRequest request = DiagnoseRequest.builder()
        .question(question)
        .knowledge(loadKnowledgeForReport(user.regionId(), user.industryId(), user.membershipLevel()))
        .recentMessages(recent)
        .conversationSummary(summary != null ? summary.summaryText() : null)
        .longTermMemories(profileMemories)
        .diagnosisMemories(diagnosisMemoryStore.asMaps(conversationId))
        .diagnosisCompleteness(conversation.profileCompleteness())
        .diagnosisMissingFields(readStringList(conversation.primaryIssueTags()))
        .profileMissingFieldsForReport(List.of())
        .additionalInformationQuestions(readObjectList(conversation.recommendedQuestionIds()))
        .regionId(user.regionId()).industryId(user.industryId())
        .membershipLevel(user.membershipLevel()).build();
    return buildFromRequest(question, request);
  }

  private List<String> readStringList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
          new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private List<Map<String, Object>> readObjectList(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
          new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception ignored) {
      return List.of();
    }
  }

  /**
   * Build a diagnosis report by delegating to the AI worker.
   *
   * <p>The worker's {@code answer} field is mapped to the report's
   * {@code summary} field.
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
    return buildFromRequest(question, request, user);
  }

  private DiagnosisReport buildFromRequest(String question, DiagnoseRequest request, UserAccount user) {
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

  /**
   * V2: Build a diagnosis report and generate it as a PDF byte array.
   *
   * <p>This method delegates to the AI worker for the answer (same as
   * {@link #build}), then renders the result into a formatted PDF document.
   *
   * @throws AiWorkerException if the worker is unavailable or LLM is not configured
   */
  public byte[] buildPdf(String question, UserAccount user, PdfReportGenerator generator) {
    DiagnosisReport report = build(question, user);
    return generator.generate(report);
  }

  // ── Knowledge loading ───────────────────────────────────────────

  private List<Map<String, Object>> loadKnowledgeForReport(
      String regionId, String industryId, String membershipLevel) {
    List<KnowledgeItem> knowledgeItems = knowledgeStore.listScoped(regionId, industryId);
    List<Map<String, Object>> combined = new ArrayList<>(knowledgeItems.size());
    for (KnowledgeItem item : knowledgeItems) {
      combined.add(knowledgeToMap(item));
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

  /* private Map<String, Object> intelligenceToMap(IntelligenceItem item) {
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
  */
}
