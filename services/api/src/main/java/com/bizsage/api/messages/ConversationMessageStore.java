package com.bizsage.api.messages;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationMessageStore {
  private final ConversationMessageMapper messageMapper;

  public ConversationMessageStore(ConversationMessageMapper messageMapper) {
    this.messageMapper = messageMapper;
  }

  public long append(
      long conversationId,
      String sender,
      String messageType,
      String content,
      String sourcesJson,
      String confidence,
      String timeliness,
      String selfCheckStatus,
      String regionId,
      String industryId) {
    ConversationMessage message = new ConversationMessage();
    message.setConversationId(conversationId);
    message.setSender(sender);
    message.setMessageType(messageType);
    message.setContent(content);
    message.setSourcesJson(sourcesJson);
    message.setConfidence(confidence);
    message.setTimeliness(timeliness);
    message.setSelfCheckStatus(selfCheckStatus);
    message.setActiveContext(true);
    message.setRegionId(regionId);
    message.setIndustryId(industryId);
    message.setSourceId("conversation");
    message.setWeight(1.0D);
    message.setCreateTime(Instant.now());
    messageMapper.insert(message);
    return message.id();
  }

  public List<ConversationMessage> recentActiveMessages(long conversationId, int limit) {
    List<ConversationMessage> items = messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
        .eq(ConversationMessage::getConversationId, conversationId)
        .eq(ConversationMessage::getActiveContext, true)
        .orderByDesc(ConversationMessage::getId)
        .last("limit " + limit));
    List<ConversationMessage> ordered = new ArrayList<>(items);
    java.util.Collections.reverse(ordered);
    return ordered;
  }

  public List<ConversationMessage> activeMessages(long conversationId) {
    return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
        .eq(ConversationMessage::getConversationId, conversationId)
        .eq(ConversationMessage::getActiveContext, true)
        .orderByAsc(ConversationMessage::getId));
  }

  public void markInactive(long conversationId, long maxMessageId, long summaryGroupId) {
    messageMapper.update(null, new LambdaUpdateWrapper<ConversationMessage>()
        .eq(ConversationMessage::getConversationId, conversationId)
        .le(ConversationMessage::getId, maxMessageId)
        .eq(ConversationMessage::getActiveContext, true)
        .set(ConversationMessage::getActiveContext, false)
        .set(ConversationMessage::getSummaryGroupId, summaryGroupId));
  }
}
