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
import com.bizsage.api.worker.DiagnoseResponse;
import com.bizsage.api.worker.LearningRequest;
import com.bizsage.api.worker.TransitionRequest;
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

/**
 * Industry Learning Agent orchestrator — the second half of BizSage's
 * dual-Agent system.
 *
 * <p>Parallels {@link DiagnosisService} but targets the AI worker's
 * {@code /agent/learn} and {@code /agent/transition} endpoints.
 * Handles message persistence, memory extraction, and SSE payload
 * assembly for learning interactions within a conversation.
 */
@Service
public class LearningService {

  private static final Logger log = LoggerFactory.getLogger(LearningService.class);

  private final AiWorkerClient aiWorkerClient;
  private final ObjectMapper objectMapper;
  private final ConversationMessageStore messageStore;
  private final ConversationSummaryStore summaryStore;
  private final UserMemoryStore userMemoryStore;
  private final UserMemoryEmbeddingStore embeddingStore;
  private final KnowledgeStore knowledgeStore;
  private final IntelligenceStore intelligenceStore;

  public LearningService(
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
   * Run an industry learning interaction by delegating to the AI worker.
   *
   * @param chainNodeId optional chain node to focus learning on (auto-detected if null)
   * @param learningMode FAST_START, FULL_CHAIN, or NODE_DEEP_DIVE
   */
  public String learn(
      Conversation conversation, UserAccount user, String question,
      String chainNodeId, String learningMode) {
    // 1. Persist the user message
    long userMessageId = messageStore.append(
        conversation.id(), "USER", "LEARNING_QUESTION", question,
        null, null, null, null,
        conversation.regionId(), conversation.industryId());

    // 2. Load context
    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    List<ConversationMessage> recentMessages = messageStore.recentActiveMessages(conversation.id(), 6);
    ConversationSummary summary = summaryStore.latestActive(conversation.id()).orElse(null);

    // 3. Load knowledge base
    List<Map<String, Object>> knowledgeItems = loadKnowledge(
        conversation.regionId(), conversation.industryId(), user.membershipLevel());

    // 4. Assemble the learning request
    LearningRequest request = LearningRequest.builder()
        .question(question)
        .knowledge(knowledgeItems)
        .chainNodeId(chainNodeId)
        .learningMode(learningMode != null ? learningMode : "FAST_START")
        .recentMessages(toRecentMessageMaps(recentMessages))
        .conversationSummary(summary != null ? summary.summaryText() : null)
        .longTermMemories(toMemoryMaps(memories))
        .regionId(conversation.regionId())
        .industryId(conversation.industryId())
        .membershipLevel(user.membershipLevel())
        .build();

    // 5. Call the AI worker
    DiagnoseResponse response;
    try {
      response = aiWorkerClient.learn(request);
    } catch (AiWorkerException ex) {
      log.error("AI worker learning failed for conversation {}: {}", conversation.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "AI worker returned an unsuccessful learning response: selfCheckStatus=" + response.selfCheckStatus());
    }

    // 6. Build the payload for SSE / persistence
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("mode", response.mode() != null ? response.mode() : "LEARNING");
    payload.put("answer", response.answer());
    payload.put("sources", toSourceMaps(response));
    payload.put("confidence", response.confidence());
    payload.put("timeliness", response.timeliness());
    payload.put("selfCheckStatus", response.selfCheckStatus());
    payload.put("disclaimer", response.disclaimer());
    payload.put("chainNodeId", response.chainNodeId());
    payload.put("suggestedActions", response.suggestedActions() != null ? response.suggestedActions() : List.of());
    if (response.sections() != null) {
      payload.put("sections", response.sections());
    }

    String payloadJson = serialize(payload);

    // 7. Persist the assistant message
    long assistantMessageId = messageStore.append(
        conversation.id(), "ASSISTANT", "LEARNING_ANSWER",
        response.answer(), serialize(toSourceMaps(response)),
        response.confidence(), response.timeliness(),
        response.selfCheckStatus(),
        conversation.regionId(), conversation.industryId());

    // 8. Persist memory candidates
    if (response.memoryCandidates() != null) {
      for (Map<String, Object> candidate : response.memoryCandidates()) {
        persistCandidate(user, conversation, userMessageId, assistantMessageId, candidate);
      }
    }

    // 9. Maintain memories and summaries
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());

    return payloadJson;
  }

  /**
   * Execute a dual-Agent mode transition within a conversation.
   * Routes to the target Agent (LEARNING ↔ DIAGNOSIS) with preserved context.
   */
  public String transition(
      Conversation conversation, UserAccount user,
      String fromMode, String toMode, String question, String chainNodeId) {
    // 1. Persist the transition message
    long userMessageId = messageStore.append(
        conversation.id(), "USER", "TRANSITION",
        question, null, null, null, null,
        conversation.regionId(), conversation.industryId());

    // 2. Load context
    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    List<ConversationMessage> recentMessages = messageStore.recentActiveMessages(conversation.id(), 6);
    ConversationSummary summary = summaryStore.latestActive(conversation.id()).orElse(null);

    // 3. Load knowledge
    List<Map<String, Object>> knowledgeItems = loadKnowledge(
        conversation.regionId(), conversation.industryId(), user.membershipLevel());

    // 4. Assemble transition request
    TransitionRequest request = TransitionRequest.builder()
        .fromMode(fromMode)
        .toMode(toMode)
        .question(question)
        .chainNodeId(chainNodeId)
        .knowledge(knowledgeItems)
        .recentMessages(toRecentMessageMaps(recentMessages))
        .conversationSummary(summary != null ? summary.summaryText() : null)
        .longTermMemories(toMemoryMaps(memories))
        .regionId(conversation.regionId())
        .industryId(conversation.industryId())
        .membershipLevel(user.membershipLevel())
        .build();

    // 5. Call AI worker transition
    DiagnoseResponse response;
    try {
      response = aiWorkerClient.transition(request);
    } catch (AiWorkerException ex) {
      log.error("AI worker transition failed for conversation {}: {}", conversation.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "AI worker transition failed: selfCheckStatus=" + response.selfCheckStatus());
    }

    // 6. Build payload
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("mode", response.mode());
    payload.put("answer", response.answer());
    payload.put("sources", toSourceMaps(response));
    payload.put("confidence", response.confidence());
    payload.put("timeliness", response.timeliness());
    payload.put("selfCheckStatus", response.selfCheckStatus());
    payload.put("disclaimer", response.disclaimer());
    payload.put("chainNodeId", response.chainNodeId());
    payload.put("suggestedActions", response.suggestedActions() != null ? response.suggestedActions() : List.of());
    if (response.sections() != null) {
      payload.put("sections", response.sections());
    }

    String payloadJson = serialize(payload);

    // 7. Persist assistant message with transition type
    String messageType = "LEARNING".equals(toMode) ? "LEARNING_ANSWER" : "ANSWER";
    messageStore.append(
        conversation.id(), "ASSISTANT", messageType,
        response.answer(), serialize(toSourceMaps(response)),
        response.confidence(), response.timeliness(),
        response.selfCheckStatus(),
        conversation.regionId(), conversation.industryId());

    // 8. Maintain memories
    if (response.memoryCandidates() != null) {
      for (Map<String, Object> candidate : response.memoryCandidates()) {
        persistCandidate(user, conversation, userMessageId, 0L, candidate);
      }
    }
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());

    return payloadJson;
  }

  // ── Knowledge loading (shared logic with DiagnosisService) ──────

  private List<Map<String, Object>> loadKnowledge(
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

  // ── Knowledge to AI Worker format ──────────────────────────────

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
    if (response.sources() == null) return List.of();
    return response.sources().stream()
        .map(src -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("id", src.id());
          map.put("title", src.title());
          map.put("sourceUrl", src.sourceUrl());
          map.put("sourceId", src.sourceId());
          map.put("confidence", src.confidence());
          if (src.score() != null) map.put("score", src.score());
          map.put("entitlement", src.entitlement() != null ? src.entitlement() : "FREE");
          return map;
        })
        .collect(Collectors.toList());
  }

  // ── Memory persistence ──────────────────────────────────────────

  private void persistCandidate(
      UserAccount user, Conversation conversation,
      long userMessageId, long assistantMessageId,
      Map<String, Object> candidate) {
    try {
      String category = stringField(candidate, "category", "PREFERENCE");
      String key = stringField(candidate, "key", "unknown");
      String value = stringField(candidate, "value", "");
      double confidence = doubleField(candidate, "confidence", 0.5);
      boolean structured = booleanField(candidate, "structured", true);

      UserMemoryProfile saved = userMemoryStore.saveOrRefresh(
          user.id(), category, key, value, "STRING", confidence,
          conversation.id(), userMessageId, structured);
      if (!structured) {
        embeddingStore.save(saved.id(), user.id(), value, conversation.id(), assistantMessageId);
      }
    } catch (Exception ex) {
      log.warn("Failed to persist learning memory candidate for user {}: {}", user.id(), ex.getMessage());
    }
  }

  // ── Summarization ───────────────────────────────────────────────

  private void summarizeIfNeeded(long conversationId) {
    List<ConversationMessage> active = messageStore.activeMessages(conversationId);
    if (active.size() < 8) return;
    int coveredCount = Math.min(4, active.size() - 4);
    if (coveredCount <= 0) return;
    List<ConversationMessage> covered = active.subList(0, coveredCount);
    String summaryText = covered.stream()
        .map(message -> message.sender() + ":" + message.content())
        .reduce((left, right) -> left + " | " + right)
        .orElse("");
    ConversationSummary summary = summaryStore.save(
        conversationId, summaryText,
        covered.getFirst().id(), covered.getLast().id());
    messageStore.markInactive(conversationId, summary.coveredMessageEndId(), summary.id());
  }

  // ── Utility ─────────────────────────────────────────────────────

  private static String stringField(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value instanceof String str ? str : defaultValue;
  }

  private static double doubleField(Map<String, Object> map, String key, double defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number num) return num.doubleValue();
    return defaultValue;
  }

  private static boolean booleanField(Map<String, Object> map, String key, boolean defaultValue) {
    Object value = map.get(key);
    if (value instanceof Boolean bool) return bool;
    return defaultValue;
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("learning serialization failed", exception);
    }
  }
}
