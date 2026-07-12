package com.bizsage.api.messages;

import com.bizsage.api.conversations.Conversation;
import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeStore;
import com.bizsage.api.memory.UserMemoryProfile;
import com.bizsage.api.memory.UserMemoryStore;
import com.bizsage.api.recommendations.RecommendationService;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.AgentStreamListener;
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
 * 行业学习 Agent 编排服务，是 BizSage 双 Agent 体系的学习链路。
 *
 * <p>职责与 {@link DiagnosisService} 类似，但调用 AI Worker 的 {@code /agent/learn}
 * 和 {@code /agent/transition}。API 层负责消息落库、记忆落库、摘要维护和 SSE payload 组装。
 */
@Service
public class LearningService {

  private static final Logger log = LoggerFactory.getLogger(LearningService.class);

  private final AiWorkerClient aiWorkerClient;
  private final ObjectMapper objectMapper;
  private final ConversationMessageStore messageStore;
  private final ConversationSummaryStore summaryStore;
  private final UserMemoryStore userMemoryStore;
  private final KnowledgeStore knowledgeStore;
  private final RecommendationService recommendationService;

  public LearningService(
      AiWorkerClient aiWorkerClient,
      ObjectMapper objectMapper,
      ConversationMessageStore messageStore,
      ConversationSummaryStore summaryStore,
      UserMemoryStore userMemoryStore,
      KnowledgeStore knowledgeStore,
      RecommendationService recommendationService) {
    this.aiWorkerClient = aiWorkerClient;
    this.objectMapper = objectMapper;
    this.messageStore = messageStore;
    this.summaryStore = summaryStore;
    this.userMemoryStore = userMemoryStore;
    this.knowledgeStore = knowledgeStore;
    this.recommendationService = recommendationService;
  }

  /**
   * 委托 AI Worker 执行一次行业学习交互。
   *
   * @param chainNodeId 可选链条节点；为空时 Worker 会尝试自动识别
   * @param learningMode FAST_START、FULL_CHAIN 或 NODE_DEEP_DIVE
   */
  public String learn(
      Conversation conversation, UserAccount user, String question,
      String chainNodeId, String learningMode) {
    return learnInternal(
        conversation, user, question, chainNodeId, learningMode, null);
  }

  public String learnStream(
      Conversation conversation, UserAccount user, String question,
      String chainNodeId, String learningMode, AgentStreamListener listener) {
    return learnInternal(
        conversation, user, question, chainNodeId, learningMode, listener);
  }

  private String learnInternal(
      Conversation conversation, UserAccount user, String question,
      String chainNodeId, String learningMode, AgentStreamListener listener) {
    // 1. 持久化用户学习问题，便于失败排查和后续摘要。
    long userMessageId = messageStore.append(
        conversation.id(), "USER", "LEARNING_QUESTION", question,
        null, null, null, null,
        conversation.regionId(), conversation.industryId());

    // 2. 加载记忆、最近消息和摘要，为学习模式提供用户背景。
    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    List<ConversationMessage> recentMessages = messageStore.recentActiveMessages(conversation.id(), 6);
    ConversationSummary summary = summaryStore.latestActive(conversation.id()).orElse(null);

    // 3. 加载用户可见的知识和已审核情报，作为学习证据池。
    List<Map<String, Object>> knowledgeItems = loadKnowledge(
        conversation.regionId(), conversation.industryId(), user.membershipLevel());

    // 4. 组装学习请求；learningMode 为空时使用快速入门默认值。
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
        .agentMode("LEARNING")
        .workflowStage("INTRO")
        .profileMissingFields(List.of())
        .recommendationBlacklist(List.of())
        .build();

    // 5. 调用 AI Worker；学习失败同样严格抛出，不使用本地答案兜底。
    DiagnoseResponse response;
    try {
      response = listener == null
          ? aiWorkerClient.learn(request)
          : aiWorkerClient.streamLearn(request, listener);
    } catch (AiWorkerException ex) {
      log.error("AI worker learning failed for conversation {}: {}", conversation.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "AI worker returned an unsuccessful learning response: selfCheckStatus=" + response.selfCheckStatus());
    }

    // 6. 构造 SSE 与持久化共用的学习响应 payload。
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
    payload.put("recommendationCandidates", response.recommendationCandidates() != null ? response.recommendationCandidates() : List.of());
    payload.put("currentTopic", response.currentTopic());
    payload.put("nextBestTopics", response.nextBestTopics() != null ? response.nextBestTopics() : List.of());
    payload.put("workflowStage", response.workflowStage());
    payload.put("profileMissingFields", response.profileMissingFields() != null ? response.profileMissingFields() : List.of());
    payload.put("completionSignal", response.completionSignal());

    String payloadJson = serialize(payload);

    // 7. 持久化助手学习回答。
    long assistantMessageId = messageStore.append(
        conversation.id(), "ASSISTANT", "LEARNING_ANSWER",
        response.answer(), serialize(toSourceMaps(response)),
        response.confidence(), response.timeliness(),
        response.selfCheckStatus(),
        conversation.regionId(), conversation.industryId());

    // 8. 持久化学习中产生的长期记忆候选。
    if (response.memoryCandidates() != null) {
      for (Map<String, Object> candidate : response.memoryCandidates()) {
        persistCandidate(user, conversation, userMessageId, assistantMessageId, candidate);
      }
    }

    recommendationService.recordUsage(extractQuestionIds(response.recommendationCandidates()));

    // 9. 标记记忆使用并滚动维护摘要。
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());

    return payloadJson;
  }

  /**
   * 在同一会话内执行双 Agent 模式切换。
   *
   * <p>Worker 会保留原模式上下文，并将请求路由到目标 Agent（LEARNING ↔ DIAGNOSIS）。
   */
  public String transition(
      Conversation conversation, UserAccount user,
      String fromMode, String toMode, String question, String chainNodeId) {
    return transitionInternal(
        conversation, user, fromMode, toMode, question, chainNodeId, null);
  }

  public String transitionStream(
      Conversation conversation, UserAccount user,
      String fromMode, String toMode, String question, String chainNodeId,
      AgentStreamListener listener) {
    return transitionInternal(
        conversation, user, fromMode, toMode, question, chainNodeId, listener);
  }

  private String transitionInternal(
      Conversation conversation, UserAccount user,
      String fromMode, String toMode, String question, String chainNodeId,
      AgentStreamListener listener) {
    // 1. 持久化用户触发切换的问题。
    long userMessageId = messageStore.append(
        conversation.id(), "USER", "TRANSITION",
        question, null, null, null, null,
        conversation.regionId(), conversation.industryId());

    // 2. 加载切换所需上下文，保证目标 Agent 不丢失原模式讨论内容。
    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    List<ConversationMessage> recentMessages = messageStore.recentActiveMessages(conversation.id(), 6);
    ConversationSummary summary = summaryStore.latestActive(conversation.id()).orElse(null);

    // 3. 切换后的目标 Agent 仍使用同一权限范围内的知识池。
    List<Map<String, Object>> knowledgeItems = loadKnowledge(
        conversation.regionId(), conversation.industryId(), user.membershipLevel());

    // 4. 组装模式切换请求。
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

    // 5. 调用 AI Worker 的模式切换接口。
    DiagnoseResponse response;
    try {
      response = listener == null
          ? aiWorkerClient.transition(request)
          : aiWorkerClient.streamTransition(request, listener);
    } catch (AiWorkerException ex) {
      log.error("AI worker transition failed for conversation {}: {}", conversation.id(), ex.getMessage());
      throw ex;
    }

    if (!response.isSuccessful()) {
      throw new AiWorkerException(
          "AI worker transition failed: selfCheckStatus=" + response.selfCheckStatus());
    }

    // 6. 构造目标模式的统一响应 payload。
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

    // 7. 按目标模式持久化助手消息类型。
    String messageType = "LEARNING".equals(toMode) ? "LEARNING_ANSWER" : "ANSWER";
    messageStore.append(
        conversation.id(), "ASSISTANT", messageType,
        response.answer(), serialize(toSourceMaps(response)),
        response.confidence(), response.timeliness(),
        response.selfCheckStatus(),
        conversation.regionId(), conversation.industryId());

    // 8. 持久化切换记忆候选并维护摘要。
    if (response.memoryCandidates() != null) {
      for (Map<String, Object> candidate : response.memoryCandidates()) {
        persistCandidate(user, conversation, userMessageId, 0L, candidate);
      }
    }
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());

    return payloadJson;
  }

  // 知识加载：与 DiagnosisService 保持同一权限口径。

  private List<Map<String, Object>> loadKnowledge(
      String regionId, String industryId, String membershipLevel) {
    // DataScope 用于筛选已审核情报，静态知识由 KnowledgeStore 按地域/行业过滤。
    List<KnowledgeItem> knowledgeItems = knowledgeStore.listScoped(regionId, industryId);
    List<Map<String, Object>> combined = new ArrayList<>(knowledgeItems.size());
    for (KnowledgeItem item : knowledgeItems) {
      combined.add(knowledgeToMap(item));
    }
    return combined;
  }

  // 上下文映射。

  private List<Map<String, Object>> toRecentMessageMaps(List<ConversationMessage> messages) {
    // Worker 使用 OpenAI 风格 role/content，不关心数据库消息类型。
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
    // 只发送提示词需要的记忆字段，避免暴露内部状态字段。
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

  // 转换为 AI Worker 知识格式。

  private Map<String, Object> knowledgeToMap(KnowledgeItem item) {
    // 静态知识使用 kb- 前缀，避免与运营情报 ID 冲突。
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

  /* private Map<String, Object> intelligenceToMap(IntelligenceItem item) {
    // 运营情报使用 intel- 前缀，权重和置信度来自审核结果。
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "intel-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.url() != null ? item.url() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "intelligence");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("authority", 0.85);     // 六维重排：后续可按来源动态赋值。
    map.put("timeliness", 0.85);    // 六维重排：后续可按采集时间动态衰减。
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
    return map;
  }

  // 响应映射。

  */
  private List<Map<String, Object>> toSourceMaps(DiagnoseResponse response) {
    // 复用 DiagnoseResponse 的来源结构，前端可统一展示学习和诊断引用。
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

  // 记忆持久化。

  private void persistCandidate(
      UserAccount user, Conversation conversation,
      long userMessageId, long assistantMessageId,
      Map<String, Object> candidate) {
    try {
      // Worker 只提供候选，UserMemoryStore 负责 save-or-refresh 和生命周期策略。
      String category = stringField(candidate, "category", "PREFERENCE");
      String key = stringField(candidate, "key", "unknown");
      String value = stringField(candidate, "value", "");
      double confidence = doubleField(candidate, "confidence", 0.5);
      boolean structured = booleanField(candidate, "structured", true);

      UserMemoryProfile saved = userMemoryStore.saveOrRefresh(
          user.id(), category, key, value, "STRING", confidence,
          conversation.id(), userMessageId, structured);
    } catch (Exception ex) {
      log.warn("Failed to persist learning memory candidate for user {}: {}", user.id(), ex.getMessage());
    }
  }

  // 会话摘要。

  private void summarizeIfNeeded(long conversationId) {
    // 与诊断链路一致：消息达到阈值后把较早内容压入摘要。
    List<ConversationMessage> active = messageStore.activeMessages(conversationId);
    if (active.size() < 8) return;
    int coveredCount = Math.min(4, active.size() - 4);
    if (coveredCount <= 0) return;
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
        conversationId, summaryText,
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
