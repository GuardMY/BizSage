package com.bizsage.api.messages;

import com.bizsage.api.conversations.Conversation;
import com.bizsage.api.conversations.ConversationStore;
import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeStore;
import com.bizsage.api.memory.UserMemoryProfile;
import com.bizsage.api.memory.UserMemoryStore;
import com.bizsage.api.memory.ConversationDiagnosisMemoryStore;
import com.bizsage.api.recommendations.RecommendationService;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.AgentStreamListener;
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
import org.springframework.beans.factory.annotation.Value;

@Service
public class DiagnosisService {

  private static final Logger log = LoggerFactory.getLogger(DiagnosisService.class);

  private final AiWorkerClient aiWorkerClient;
  private final ObjectMapper objectMapper;
  private final ConversationMessageStore messageStore;
  private final ConversationSummaryStore summaryStore;
  private final UserMemoryStore userMemoryStore;
  private final KnowledgeStore knowledgeStore;
  private final RecommendationService recommendationService;
  private final ConversationStore conversationStore;
  private final ConversationDiagnosisMemoryStore diagnosisMemoryStore;
  private final double completenessThreshold;

  public DiagnosisService(
      AiWorkerClient aiWorkerClient,
      ObjectMapper objectMapper,
      ConversationMessageStore messageStore,
      ConversationSummaryStore summaryStore,
      UserMemoryStore userMemoryStore,
      ConversationStore conversationStore,
      KnowledgeStore knowledgeStore,
      RecommendationService recommendationService,
      ConversationDiagnosisMemoryStore diagnosisMemoryStore,
      @Value("${bizsage.diagnosis.completeness-threshold:80}") double completenessThreshold) {
    this.aiWorkerClient = aiWorkerClient;
    this.objectMapper = objectMapper;
    this.messageStore = messageStore;
    this.summaryStore = summaryStore;
    this.userMemoryStore = userMemoryStore;
    this.conversationStore = conversationStore;
    this.knowledgeStore = knowledgeStore;
    this.recommendationService = recommendationService;
    this.diagnosisMemoryStore = diagnosisMemoryStore;
    this.completenessThreshold = completenessThreshold;
  }

/**
   * 委托 AI Worker 执行经营诊断。
   *
   * <p>API 层负责消息落库、记忆落库和摘要维护；AI Worker 是唯一推理引擎。
   *
   * @throws AiWorkerException Worker 不可用或 LLM 未配置时抛出
   */
  public String diagnose(Conversation conversation, UserAccount user, String question) {
    return diagnoseInternal(conversation, user, question, null);
  }

  public String diagnoseStream(
      Conversation conversation,
      UserAccount user,
      String question,
      AgentStreamListener listener) {
    return diagnoseInternal(conversation, user, question, listener);
  }

  private String diagnoseInternal(
      Conversation conversation,
      UserAccount user,
      String question,
      AgentStreamListener listener) {
    // 1. 先持久化用户问题，保证 Worker 失败时也能追踪本次提问。
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

    // 2. 加载 Worker 需要的上下文：长期记忆、最近消息和会话摘要。
    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    List<ConversationMessage> recentMessages = messageStore.recentActiveMessages(conversation.id(), 6);
    ConversationSummary summary = summaryStore.latestActive(conversation.id()).orElse(null);

    // 3. 加载本用户可见的知识与已审核情报，作为 RAG 证据池。
    List<Map<String, Object>> knowledgeItems = loadKnowledgeForDiagnosis(
        conversation.regionId(), conversation.industryId(), user.membershipLevel());

    // 4. 组装 Worker 请求；字段命名需与 Python Pydantic 模型保持兼容。
    DiagnoseRequest request = DiagnoseRequest.builder()
        .question(question)
        .knowledge(knowledgeItems)
        .recentMessages(toRecentMessageMaps(recentMessages))
        .conversationSummary(summary != null ? summary.summaryText() : null)
        .longTermMemories(toMemoryMaps(memories))
        .diagnosisMemories(diagnosisMemoryStore.asMaps(conversation.id()))
        .regionId(conversation.regionId())
        .industryId(conversation.industryId())
        .membershipLevel(user.membershipLevel())
        .agentMode("DIAGNOSIS")
        .workflowStage("INTRO")
        .profileMissingFields(List.of())
        .recommendedQuestionIds(List.of())
        .diagnosisClosable(false)
        .build();

    // 5. 调用 AI Worker；严格失败，不做本地答案兜底。
    DiagnoseResponse response;
    try {
      response = listener == null
          ? aiWorkerClient.diagnose(request)
          : aiWorkerClient.streamDiagnose(request, listener);
    } catch (AiWorkerException ex) {
      log.error("AI worker diagnosis failed for conversation {}: {}", conversation.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "AI worker returned an unsuccessful diagnosis: selfCheckStatus=" + response.selfCheckStatus());
    }

    // 6. 构造 SSE 与持久化共用的响应 payload。
    List<Map<String, Object>> sources = toSourceMaps(response);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("answer", response.answer());
    payload.put("sources", sources);
    payload.put("confidence", response.confidence());
    payload.put("timeliness", response.timeliness());
    payload.put("selfCheckStatus", response.selfCheckStatus());
    payload.put("disclaimer", response.disclaimer());
    payload.put("workflowStage", response.workflowStage());
    payload.put("profileMissingFields", response.profileMissingFields() != null ? response.profileMissingFields() : List.of());
    double completeness = response.diagnosisCompleteness() != null ? response.diagnosisCompleteness() : 0D;
    payload.put("diagnosisCompleteness", completeness);
    payload.put("diagnosisCompletenessThreshold", completenessThreshold);
    payload.put("reportReady", completeness >= completenessThreshold);
    payload.put("userProfileMemories", response.userProfileMemories() != null ? response.userProfileMemories() : List.of());
    payload.put("diagnosisMemories", response.diagnosisMemories() != null ? response.diagnosisMemories() : List.of());
    payload.put("diagnosisMissingFields", response.diagnosisMissingFields() != null ? response.diagnosisMissingFields() : List.of());
    payload.put("additionalInformationQuestions", response.additionalInformationQuestions() != null ? response.additionalInformationQuestions() : List.of());
    payload.put("completionSignal", response.completionSignal());
    payload.put("recommendedQuestions", response.recommendedQuestions() != null ? response.recommendedQuestions() : List.of());
    payload.put("recommendedQuestionIds", response.recommendedQuestions() != null
        ? response.recommendedQuestions().stream()
            .map(item -> item.get("id"))
            .toList()
        : List.of());

    String payloadJson = serialize(payload);

    // 7. 持久化助手回答及其来源、自检状态。
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

    // 8. 持久化 Worker 从本轮问答中提取的长期记忆候选。
    if (response.memoryCandidates() != null) {
      for (Map<String, Object> candidate : response.memoryCandidates()) {
        persistCandidate(user, conversation, userMessageId, assistantMessageId, candidate);
      }
    }

    diagnosisMemoryStore.saveAll(
        conversation.id(),
        assistantMessageId,
        response.diagnosisMemories());
    updateConversationState(conversation, response, completeness);

    recommendationService.recordUsage(extractQuestionIds(response.recommendedQuestions()));

    // 9. 标记记忆使用并维护会话摘要，防止活跃消息无限增长。
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());

    return payloadJson;
  }

  private void updateConversationState(Conversation conversation, DiagnoseResponse response, double completeness) {
    try {
      String questions = serialize(response.additionalInformationQuestions() != null
          ? response.additionalInformationQuestions() : List.of());
      conversationStore.updateWorkflow(
          conversation.id(), conversation.ownerUsername(), "DIAGNOSIS", "IN_PROGRESS",
          completeness, serialize(response.diagnosisMissingFields() != null
              ? response.diagnosisMissingFields() : List.of()), questions, null, null);
    } catch (Exception ex) {
      log.warn("Failed to update diagnosis state for conversation {}: {}", conversation.id(), ex.getMessage());
    }
  }

  // 上下文映射。

  private List<Map<String, Object>> toRecentMessageMaps(List<ConversationMessage> messages) {
    // Worker 只需要 OpenAI 风格 role/content，数据库消息类型在这里收敛。
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
    // 只发送生成提示词所需的记忆字段，避免泄露数据库内部状态。
    return memories.stream()
        .map(mem -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("category", mem.category());
          map.put("key", mem.key());
          map.put("value", mem.value());
          map.put("confidence", mem.confidence());
          map.put("source", "mysql:user_memory_profiles");
          return map;
        })
        .collect(Collectors.toList());
  }

  // 知识加载。

  /**
   * 加载知识库与已审核情报，作为 AI Worker 的 RAG 语料。
   *
   * <p>地域/行业范围由 Store 层和 DataScope 控制，避免把不可见情报传给 Worker。
   */
  private List<Map<String, Object>> loadKnowledgeForDiagnosis(
      String regionId, String industryId, String membershipLevel) {
    List<KnowledgeItem> knowledgeItems = knowledgeStore.listScoped(regionId, industryId);
    List<Map<String, Object>> combined = new ArrayList<>(knowledgeItems.size());
    for (KnowledgeItem item : knowledgeItems) {
      combined.add(knowledgeToMap(item));
    }
    return combined;
  }

  private Map<String, Object> knowledgeToMap(KnowledgeItem item) {
    // 以 kb- 前缀区分静态知识，避免与情报 ID 冲突。
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "kb-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.sourceUrl() != null ? item.sourceUrl() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "knowledge");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("authority", 0.85);     // 六维重排：静态知识默认中高权威。
    map.put("timeliness", 0.85);    // 六维重排：静态知识默认中高时效。
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
    return map;
  }

  /* private Map<String, Object> intelligenceToMap(IntelligenceItem item) {
    // 以 intel- 前缀区分运营情报，来源和权重来自审核入库结果。
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "intel-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.url() != null ? item.url() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "intelligence");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("authority", 0.85);     // 六维重排：后续可接入来源级权威度。
    map.put("timeliness", 0.85);    // 六维重排：后续可接入情报采集时间衰减。
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
    return map;
  }

  */
  private List<Long> extractQuestionIds(List<Map<String, Object>> candidates) {
    if (candidates == null) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>();
    for (Map<String, Object> candidate : candidates) {
      Object id = candidate.get("id");
      if (id instanceof Number number) {
        ids.add(number.longValue());
      }
    }
    return ids;
  }

  // 响应映射。

  private List<Map<String, Object>> toSourceMaps(DiagnoseResponse response) {
    // 保留 Worker 返回的来源分数和权益信息，前端可用于引用展示。
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

  // 记忆持久化。

  private void persistCandidate(
      UserAccount user,
      Conversation conversation,
      long userMessageId,
      long assistantMessageId,
      Map<String, Object> candidate) {
    try {
      // Worker 只产出候选；最终去重、刷新和过期策略由 UserMemoryStore 控制。
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
    } catch (Exception ex) {
      log.warn("Failed to persist memory candidate for user {}: {}", user.id(), ex.getMessage());
    }
  }

  // 会话摘要。

  private void summarizeIfNeeded(long conversationId) {
    // 只在活跃消息足够多时滚动摘要，保留最近 4 条作为短期上下文。
    List<ConversationMessage> active = messageStore.activeMessages(conversationId);
    if (active.size() < 8) {
      return;
    }
    int coveredCount = Math.min(4, active.size() - 4);
    if (coveredCount <= 0) {
      return;
    }
    List<ConversationMessage> covered = active.subList(0, coveredCount);
    ConversationSummary latestSummary = summaryStore.latestActive(conversationId).orElse(null);
    String coveredText = covered.stream()
        .map(message -> message.sender() + ":" + message.content())
        .reduce((left, right) -> left + " | " + right)
        .orElse("");
    String summaryText = latestSummary == null || latestSummary.summaryText().isBlank()
        ? coveredText
        : latestSummary.summaryText() + " | " + coveredText;
    ConversationSummary summary = summaryStore.save(
        conversationId,
        summaryText,
        latestSummary != null ? latestSummary.coveredMessageStartId() : covered.getFirst().id(),
        covered.getLast().id());
    messageStore.markInactive(conversationId, summary.coveredMessageEndId(), summary.id());
  }

  // 小型字段解析工具。

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
