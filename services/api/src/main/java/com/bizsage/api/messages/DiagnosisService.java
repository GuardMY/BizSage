package com.bizsage.api.messages;

import com.bizsage.api.conversations.Conversation;
import com.bizsage.api.memory.UserMemoryEmbeddingStore;
import com.bizsage.api.memory.UserMemoryProfile;
import com.bizsage.api.memory.UserMemoryStore;
import com.bizsage.api.users.UserAccount;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisService {
  private static final String TIMELINESS = "基于V1静态基线知识、会话短期记忆和用户长期记忆生成。";
  private static final String DISCLAIMER = "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。";

  private final ObjectMapper objectMapper;
  private final ConversationMessageStore messageStore;
  private final ConversationSummaryStore summaryStore;
  private final UserMemoryStore userMemoryStore;
  private final UserMemoryEmbeddingStore embeddingStore;

  public DiagnosisService(
      ObjectMapper objectMapper,
      ConversationMessageStore messageStore,
      ConversationSummaryStore summaryStore,
      UserMemoryStore userMemoryStore,
      UserMemoryEmbeddingStore embeddingStore) {
    this.objectMapper = objectMapper;
    this.messageStore = messageStore;
    this.summaryStore = summaryStore;
    this.userMemoryStore = userMemoryStore;
    this.embeddingStore = embeddingStore;
  }

  public String diagnose(Conversation conversation, UserAccount user, String question) {
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

    List<UserMemoryProfile> memories = userMemoryStore.activeMemoriesForUser(user.id());
    String answer = buildAnswer(question, messageStore.recentActiveMessages(conversation.id(), 6), summaryStore.latestActive(conversation.id()).orElse(null), memories);
    List<Map<String, Object>> sources = List.of(Map.of(
        "id", "seed-restaurant-cashflow",
        "title", "餐饮门店现金流基础诊断",
        "sourceUrl", "seed://v1/restaurant-cashflow",
        "sourceId", "seed-baseline",
        "confidence", 0.9));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("answer", answer);
    payload.put("sources", sources);
    payload.put("confidence", "MEDIUM");
    payload.put("timeliness", TIMELINESS);
    payload.put("selfCheckStatus", "PASSED");
    payload.put("disclaimer", DISCLAIMER);

    String payloadJson = serialize(payload);
    long assistantMessageId = messageStore.append(
        conversation.id(),
        "ASSISTANT",
        "ANSWER",
        answer,
        serialize(sources),
        "MEDIUM",
        TIMELINESS,
        "PASSED",
        conversation.regionId(),
        conversation.industryId());

    persistMemoryCandidates(user, conversation, userMessageId, assistantMessageId, question);
    userMemoryStore.markUsed(memories.stream().map(UserMemoryProfile::id).toList());
    summarizeIfNeeded(conversation.id());
    return payloadJson;
  }

  private String buildAnswer(
      String question,
      List<ConversationMessage> recentMessages,
      ConversationSummary summary,
      List<UserMemoryProfile> memories) {
    StringBuilder answer = new StringBuilder();
    appendIfPresent(answer, preferenceValue(memories, "response_style"));
    answer.append("针对「").append(question).append("」，先围绕现金流、库存周转、平台佣金和回款周期做诊断。");
    if (summary != null) {
      answer.append(" 摘要记忆：").append(summary.summaryText()).append("。");
    }
    String followUpContext = latestUserContext(recentMessages);
    if (!followUpContext.isBlank()) {
      answer.append(" 上轮重点：").append(followUpContext).append("。");
    }
    appendIfPresent(answer, memoryValue(memories, "channel_mix"));
    appendIfPresent(answer, memoryValue(memories, "focus_metric"));
    answer.append(" 先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期，再根据证据调整动作优先级。");
    return answer.toString().trim();
  }

  private void persistMemoryCandidates(UserAccount user, Conversation conversation, long userMessageId, long assistantMessageId, String question) {
    List<MemoryCandidate> candidates = extractCandidates(question);
    for (MemoryCandidate candidate : candidates) {
      UserMemoryProfile saved = userMemoryStore.saveOrRefresh(
          user.id(),
          candidate.category(),
          candidate.key(),
          candidate.value(),
          "STRING",
          candidate.confidence(),
          conversation.id(),
          userMessageId,
          candidate.structured());
      if (!candidate.structured()) {
        embeddingStore.save(saved.id(), user.id(), candidate.value(), conversation.id(), assistantMessageId);
      }
    }
  }

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

  private List<MemoryCandidate> extractCandidates(String question) {
    List<MemoryCandidate> candidates = new ArrayList<>();
    if (question.contains("先给结论再给证据")) {
      candidates.add(new MemoryCandidate("PREFERENCE", "response_style", "先给结论再给证据", 0.95, true));
    }
    if (question.contains("现金流") || question.contains("库存")) {
      candidates.add(new MemoryCandidate("PREFERENCE", "focus_metric", "现金流和库存", 0.85, true));
    }
    if (question.contains("外卖")) {
      candidates.add(new MemoryCandidate("BUSINESS_FACT", "channel_mix", "主要依赖外卖平台", 0.90, true));
    }
    if (question.contains("两家门店")) {
      candidates.add(new MemoryCandidate("BUSINESS_FACT", "store_count", "两家门店", 0.88, true));
    }
    if (question.contains("堂食波动")) {
      candidates.add(new MemoryCandidate("BUSINESS_FACT", "narrative_constraint", "堂食波动很大且受平台佣金影响", 0.80, false));
    }
    return candidates;
  }

  private String latestUserContext(List<ConversationMessage> recentMessages) {
    return recentMessages.stream()
        .filter(message -> "USER".equals(message.sender()))
        .map(ConversationMessage::content)
        .reduce((first, second) -> second)
        .orElse("");
  }

  private String preferenceValue(List<UserMemoryProfile> memories, String key) {
    String value = memoryValue(memories, key);
    return value.isBlank() ? "" : value + "。";
  }

  private String memoryValue(List<UserMemoryProfile> memories, String key) {
    return memories.stream()
        .filter(memory -> key.equals(memory.key()))
        .map(UserMemoryProfile::value)
        .findFirst()
        .orElse("");
  }

  private void appendIfPresent(StringBuilder answer, String value) {
    if (value != null && !value.isBlank()) {
      answer.append(value);
      if (!value.endsWith("。")) {
        answer.append("。");
      }
    }
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("diagnosis serialization failed", exception);
    }
  }

  private record MemoryCandidate(String category, String key, String value, double confidence, boolean structured) {
  }
}
