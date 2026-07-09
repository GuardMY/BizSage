package com.bizsage.api.messages;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ConversationSummaryStore {
  private final ConversationSummaryMapper summaryMapper;

  public ConversationSummaryStore(ConversationSummaryMapper summaryMapper) {
    this.summaryMapper = summaryMapper;
  }

  public Optional<ConversationSummary> latestActive(long conversationId) {
    List<ConversationSummary> items = summaryMapper.selectList(new LambdaQueryWrapper<ConversationSummary>()
        .eq(ConversationSummary::getConversationId, conversationId)
        .eq(ConversationSummary::getActive, true)
        .orderByDesc(ConversationSummary::getId)
        .last("limit 1"));
    return items.stream().findFirst();
  }

  public ConversationSummary save(long conversationId, String summaryText, long startId, long endId) {
    int nextVersion = summaryMapper.selectList(new LambdaQueryWrapper<ConversationSummary>()
        .eq(ConversationSummary::getConversationId, conversationId)
        .orderByDesc(ConversationSummary::getSummaryVersion)
        .last("limit 1"))
        .stream()
        .findFirst()
        .map(ConversationSummary::getSummaryVersion)
        .orElse(0) + 1;
    ConversationSummary summary = new ConversationSummary();
    summary.setConversationId(conversationId);
    summary.setSummaryText(summaryText);
    summary.setCoveredMessageStartId(startId);
    summary.setCoveredMessageEndId(endId);
    summary.setSummaryVersion(nextVersion);
    summary.setActive(true);
    summary.setSourceId("summary");
    summary.setWeight(1.0D);
    summaryMapper.insert(summary);
    return summary;
  }
}
