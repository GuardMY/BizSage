package com.bizsage.api.recommendations;

import com.bizsage.api.conversations.Conversation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
  private final QuestionPoolStore questionPoolStore;

  public RecommendationService(QuestionPoolStore questionPoolStore) {
    this.questionPoolStore = questionPoolStore;
  }

  public RecommendationDtos.RecommendationResponse build(Conversation conversation, String agentMode,
      String workflowStage, List<String> blacklist) {
    List<QuestionPoolItem> items = questionPoolStore.list(conversation.industryId(), conversation.regionId(), agentMode);
    return buildResponse(agentMode, workflowStage, blacklist, items);
  }

  public RecommendationDtos.RecommendationResponse buildIndustryOverview(Conversation conversation,
      String agentMode, String workflowStage, List<String> blacklist) {
    List<QuestionPoolItem> items = questionPoolStore.list(null, conversation.regionId(), agentMode);
    return buildResponse(agentMode, workflowStage, blacklist, items);
  }

  private RecommendationDtos.RecommendationResponse buildResponse(String agentMode, String workflowStage,
      List<String> blacklist, List<QuestionPoolItem> items) {
    List<String> blacklistSafe = blacklist == null ? List.of() : blacklist;
    List<RecommendationDtos.RecommendationItem> result = new ArrayList<>();
    for (QuestionPoolItem item : items) {
      if (blacklistSafe.contains(item.questionKey())) {
        continue;
      }
      double score = hybridScore(item);
      result.add(new RecommendationDtos.RecommendationItem(
          item.id(),
          item.questionKey(),
          item.category(),
          item.questionText(),
          score,
          item.topLevelScore(),
          item.usageCount(),
          item.ratingAvg(),
          item.ratingCount(),
          item.sourceType(),
          item.sourceRef(),
          item.industryId()));
    }
    Map<String, List<RecommendationDtos.RecommendationItem>> grouped = new LinkedHashMap<>();
    for (RecommendationDtos.RecommendationItem item : result) {
      grouped.computeIfAbsent(item.industryId(), key -> new ArrayList<>()).add(item);
    }
    return new RecommendationDtos.RecommendationResponse(
        agentMode,
        workflowStage,
        true,
        result.stream().limit(8).collect(Collectors.toList()), grouped);
  }

  public void recordUsage(List<Long> ids) {
    questionPoolStore.recordUsage(ids);
  }

  private double hybridScore(QuestionPoolItem item) {
    double usageBoost = Math.log10(item.usageCount() + 1D) * 0.05D;
    double ratingBoost = item.ratingAvg() * 0.2D;
    return item.topLevelScore() + usageBoost + ratingBoost;
  }
}
