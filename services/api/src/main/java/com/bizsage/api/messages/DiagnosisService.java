package com.bizsage.api.messages;

import com.bizsage.api.conversations.Conversation;
import com.bizsage.api.governance.DataScope;
import com.bizsage.api.intelligence.IntelligenceItem;
import com.bizsage.api.intelligence.IntelligenceStore;
import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeStore;
import com.bizsage.api.memory.UserMemoryEmbeddingStore;
import com.bizsage.api.memory.UserMemoryProfile;
import com.bizsage.api.memory.UserMemoryStore;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.DiagnoseRequest;
import com.bizsage.api.worker.DiagnoseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisService {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

  private final AiWorkerClient aiWorkerClient;
  private final ObjectMapper objectMapper;
  private final ConversationMessageStore messageStore;
  private final ConversationSummaryStore summaryStore;
  private final UserMemoryStore userMemoryStore;
  private final UserMemoryEmbeddingStore embeddingStore;
  private final KnowledgeStore knowledgeStore;
  private final IntelligenceStore intelligenceStore;

  public DiagnosisService(
      AiWorkerClient aiWorkerClient,
      ObjectMapper objectMapper,
      ConversationMessageStore messageStore,
      ConversationSummaryStore summaryStore,
      UserMemoryStore userMemoryStore,
      UserMemoryEmbeddingStore embeddingStore,
      KnowledgeStore knowledgeStore,
      IntelligenceStore intelligenceStore) {
    this.aiWorkerClient = aiWorkerClient;
    this.objectMapper = objectMapper;
    this.messageStore = messageStore;
    this.summaryStore = summaryStore;
    this.userMemoryStore = userMemoryStore;
    this.embeddingStore = embeddingStore;
    this.knowledgeStore = knowledgeStore;
    this.intelligenceStore = intelligenceStore;
  }

  /**
   * Run a diagnosis by delegating to the AI worker.
   *
   * <p>Message persistence, memory persistence, and summarization
   * remain in the API layer.  The worker is the sole inference engine.
   *
   * @throws AiWorkerException if the worker is unavailable or LLM is not configured
   */
  public String diagnose(Conversation conversation, UserAccount user, String question) {
    // 1. Persist the user message
    long userMessageId = messageStore.append(
        conversation.id(),
        "USER",
        "QUESTION",
        question,
        null,
        null,
        null,
        null,
        conversation.regionId(),
        conversation.industryId());

    // 2. Load context for the worker
    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    List<ConversationMessage> recentMessages = messageStore.recentActiveMessages(conversation.id(), 6);
    ConversationSummary summary = summaryStore.latestActive(conversation.id()).orElse(null);

    // 3. Load knowledge base for the worker
    List<Map<String, Object>> knowledgeItems = loadKnowledgeForDiagnosis(
        conversation.regionId(), conversation.industryId(), user.membershipLevel());

    // 4. Assemble the worker request
    DiagnoseRequest request = DiagnoseRequest.builder()
        .question(question)
        .knowledge(knowledgeItems)
        .recentMessages(toRecentMessageMaps(recentMessages))
        .conversationSummary(summary != null ? summary.summaryText() : null)
        .longTermMemories(toMemoryMaps(memories))
        .regionId(conversation.regionId())
        .industryId(conversation.industryId())
        .membershipLevel(user.membershipLevel())
        .build();

    // 5. Call the AI worker — strict failure on error
    DiagnoseResponse response;
    try {
      response = aiWorkerClient.diagnose(request);
    } catch (AiWorkerException ex) {
      log.error("AI worker diagnosis failed for conversation {}: {}", conversation.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "AI worker returned an unsuccessful diagnosis: selfCheckStatus=" + response.selfCheckStatus());
    }

    // 6. Build the payload for SSE / persistence
    List<Map<String, Object>> sources = toSourceMaps(response);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("answer", response.answer());
    payload.put("sources", sources);
    payload.put("confidence", response.confidence());
    payload.put("timeliness", response.timeliness());
    payload.put("selfCheckStatus", response.selfCheckStatus());
    payload.put("disclaimer", response.disclaimer());

    String payloadJson = serialize(payload);

    // 7. Persist the assistant message
    long assistantMessageId = messageStore.append(
        conversation.id(),
        "ASSISTANT",
        "ANSWER",
        response.answer(),
        serialize(sources),
        response.confidence(),
        response.timeliness(),
        response.selfCheckStatus(),
        conversation.regionId(),
        conversation.industryId());

    // 8. Persist memory candidates from the worker
    if (response.memoryCandidates() != null) {
      for (Map<String, Object> candidate : response.memoryCandidates()) {
        persistCandidate(user, conversation, userMessageId, assistantMessageId, candidate);
      }
    }

    // 9. Mark memories as used and maintain summaries
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());

    return payloadJson;
  }

  // ── Context mappers ────────────────────────────────────────────

  private List<Map<String, Object>> toRecentMessageMaps(List<ConversationMessage> messages) {
    return messages.stream()
        .map(msg -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("role", "USER".equals(msg.sender()) ? "user" : "assistant");
          map.put("content", msg.content());
          return map;
        })
        .collect(Collectors.toList());
  }

  private List<Map<String, Object>> toMemoryMaps(List<UserMemoryProfile> memories) {
    return memories.stream()
        .map(mem -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("category", mem.category());
          map.put("key", mem.key());
          map.put("value", mem.value());
          map.put("confidence", mem.confidence());
          return map;
        })
        .collect(Collectors.toList());
  }

  // ── Knowledge loading ───────────────────────────────────────────

  /**
   * Load knowledge items from the knowledge base and approved intelligence
   * for use as the RAG search corpus in the AI worker.
   *
   * <p>Admin users (null region/industry) receive all knowledge items;
   * scoped users receive only items matching their region and industry.
   */
  private List<Map<String, Object>> loadKnowledgeForDiagnosis(
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

  // ── Response mappers ────────────────────────────────────────────

  private List<Map<String, Object>> toSourceMaps(DiagnoseResponse response) {
    if (response.sources() == null) {
      return List.of();
    }
    return response.sources().stream()
        .map(src -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("id", src.id());
          map.put("title", src.title());
          map.put("sourceUrl", src.sourceUrl());
          map.put("sourceId", src.sourceId());
          map.put("confidence", src.confidence());
          if (src.score() != null) {
            map.put("score", src.score());
          }
          map.put("entitlement", src.entitlement() != null ? src.entitlement() : "FREE");
          return map;
        })
        .collect(Collectors.toList());
  }

  // ── Memory persistence ──────────────────────────────────────────

  private void persistCandidate(
      UserAccount user,
      Conversation conversation,
      long userMessageId,
      long assistantMessageId,
      Map<String, Object> candidate) {
    try {
      String category = stringField(candidate, "category", "PREFERENCE");
      String key = stringField(candidate, "key", "unknown");
      String value = stringField(candidate, "value", "");
      double confidence = doubleField(candidate, "confidence", 0.5);
      boolean structured = booleanField(candidate, "structured", true);

      UserMemoryProfile saved = userMemoryStore.saveOrRefresh(
          user.id(),
          category,
          key,
          value,
          "STRING",
          confidence,
          conversation.id(),
          userMessageId,
          structured);
      if (!structured) {
        embeddingStore.save(saved.id(), user.id(), value, conversation.id(), assistantMessageId);
      }
    } catch (Exception ex) {
      log.warn("Failed to persist memory candidate for user {}: {}", user.id(), ex.getMessage());
    }
  }

  // ── Summarization ───────────────────────────────────────────────

  private void summarizeIfNeeded(long conversationId) {
    List<ConversationMessage> active = messageStore.activeMessages(conversationId);
    if (active.size() < 8) {
      return;
    }
    int coveredCount = Math.min(4, active.size() - 4);
    if (coveredCount <= 0) {
      return;
    }
    List<ConversationMessage> covered = active.subList(0, coveredCount);
    String summaryText = covered.stream()
        .map(message -> message.sender() + ":" + message.content())
        .reduce((left, right) -> left + " | " + right)
        .orElse("");
    ConversationSummary summary = summaryStore.save(
        conversationId,
        summaryText,
        covered.getFirst().id(),
        covered.getLast().id());
    messageStore.markInactive(conversationId, summary.coveredMessageEndId(), summary.id());
  }

  // ── Utility ─────────────────────────────────────────────────────

  private static String stringField(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value instanceof String str ? str : defaultValue;
  }

  private static double doubleField(Map<String, Object> map, String key, double defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number num) {
      return num.doubleValue();
    }
    return defaultValue;
  }

  private static boolean booleanField(Map<String, Object> map, String key, boolean defaultValue) {
    Object value = map.get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    return defaultValue;
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("diagnosis serialization failed", exception);
    }
  }
}
