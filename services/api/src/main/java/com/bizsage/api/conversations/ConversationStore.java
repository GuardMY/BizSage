package com.bizsage.api.conversations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bizsage.api.users.UserAccount;
import com.bizsage.api.users.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationStore {
  private final ConversationMapper conversationMapper;
  private final UserMapper userMapper;

  public ConversationStore(ConversationMapper conversationMapper, UserMapper userMapper) {
    this.conversationMapper = conversationMapper;
    this.userMapper = userMapper;
  }

  public Conversation create(String ownerUsername, String title, String regionId, String industryId) {
    UserAccount user = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
        .eq(UserAccount::getUsername, ownerUsername)
        .last("limit 1"));
    Conversation conversation = new Conversation();
    conversation.setUserId(user == null ? 0L : user.getId());
    conversation.setOwnerUsername(ownerUsername);
    conversation.setTitle(title);
    conversation.setStatus("ACTIVE");
    conversation.setRegionId(regionId);
    conversation.setIndustryId(industryId);
    conversation.setSourceId("user");
    conversation.setWeight(1.0D);
    conversation.setAgentMode("DIAGNOSIS");
    conversation.setWorkflowStage("INTRO");
    conversation.setProfileCompleteness(0.0D);
    conversationMapper.insert(conversation);
    return findForOwner(ownerUsername, conversation.id());
  }

  public List<Conversation> listFor(String ownerUsername) {
    return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
        .eq(Conversation::getOwnerUsername, ownerUsername)
        .ne(Conversation::getStatus, "DELETED")
        .orderByAsc(Conversation::getId));
  }

  public Conversation archive(String ownerUsername, long id) {
    int updated = conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
        .eq(Conversation::getId, id)
        .eq(Conversation::getOwnerUsername, ownerUsername)
        .set(Conversation::getStatus, "ARCHIVED"));
    if (updated == 0) {
      throw new IllegalArgumentException("conversation not found");
    }
    return findForOwner(ownerUsername, id);
  }

  public Conversation getForOwner(String ownerUsername, long id) {
    return findForOwner(ownerUsername, id);
  }

  public Conversation softDelete(String ownerUsername, long id) {
    int updated = conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
        .eq(Conversation::getId, id)
        .eq(Conversation::getOwnerUsername, ownerUsername)
        .eq(Conversation::getStatus, "ARCHIVED")
        .set(Conversation::getStatus, "DELETED"));
    if (updated == 0) {
      throw new IllegalArgumentException("conversation not found");
    }
    return findForOwner(ownerUsername, id);
  }

  public Conversation updateWorkflow(long id, String ownerUsername, String agentMode,
      String workflowStage, Double profileCompleteness, String primaryIssueTags,
      String recommendedQuestionIds, String closedBy, String closedReason) {
    int updated = conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
        .eq(Conversation::getId, id)
        .eq(Conversation::getOwnerUsername, ownerUsername)
        .set(agentMode != null, Conversation::getAgentMode, agentMode)
        .set(workflowStage != null, Conversation::getWorkflowStage, workflowStage)
        .set(profileCompleteness != null, Conversation::getProfileCompleteness, profileCompleteness)
        .set(primaryIssueTags != null, Conversation::getPrimaryIssueTags, primaryIssueTags)
        .set(recommendedQuestionIds != null, Conversation::getRecommendedQuestionIds, recommendedQuestionIds)
        .set(closedBy != null, Conversation::getClosedBy, closedBy)
        .set(closedReason != null, Conversation::getClosedReason, closedReason));
    if (updated == 0) {
      throw new IllegalArgumentException("conversation not found");
    }
    return findForOwner(ownerUsername, id);
  }

  private Conversation findForOwner(String ownerUsername, long id) {
    Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
        .eq(Conversation::getId, id)
        .eq(Conversation::getOwnerUsername, ownerUsername)
        .last("limit 1"));
    if (conversation == null) {
      throw new IllegalArgumentException("conversation not found");
    }
    return conversation;
  }
}
