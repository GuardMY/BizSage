package com.bizsage.api.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConflictStore {
  private final ConflictResolutionMapper conflictResolutionMapper;
  private final FalseLedgerMapper falseLedgerMapper;

  public ConflictStore(ConflictResolutionMapper conflictResolutionMapper, FalseLedgerMapper falseLedgerMapper) {
    this.conflictResolutionMapper = conflictResolutionMapper;
    this.falseLedgerMapper = falseLedgerMapper;
  }

  public record ConflictResolutionRecord(
      long id,
      Long incomingIntelligenceId,
      Long existingIntelligenceId,
      String conflictBranch,
      int simHashDistance,
      double incomingWeight,
      double existingWeight,
      String routingAction,
      Long reviewTicketId,
      String resolvedBy,
      String notes,
      String regionId,
      String industryId,
      String linkId,
      String createTime) {}

  public record FalseLedgerItem(
      long id,
      Long originalIntelligenceId,
      String title,
      String contentHash,
      String conflictReason,
      String matchedRumorKeyword,
      String regionId,
      String industryId,
      String archivedBy,
      String createTime) {}

  public ConflictResolutionRecord recordConflict(
      Long incomingIntelId,
      Long existingIntelId,
      String conflictBranch,
      int simHashDistance,
      double incomingWeight,
      double existingWeight,
      String routingAction,
      Long reviewTicketId,
      String resolvedBy,
      String notes,
      String regionId,
      String industryId,
      String linkId) {
    ConflictResolutionEntity entity = new ConflictResolutionEntity();
    entity.setIncomingIntelligenceId(incomingIntelId);
    entity.setExistingIntelligenceId(existingIntelId);
    entity.setConflictBranch(conflictBranch);
    entity.setSimHashDistance(simHashDistance);
    entity.setIncomingWeight(incomingWeight);
    entity.setExistingWeight(existingWeight);
    entity.setRoutingAction(routingAction);
    entity.setReviewTicketId(reviewTicketId);
    entity.setResolvedBy(resolvedBy);
    entity.setNotes(notes);
    entity.setRegionId(regionId);
    entity.setIndustryId(industryId);
    entity.setLinkId(linkId);
    conflictResolutionMapper.insert(entity);
    return findResolution(entity.getId());
  }

  public FalseLedgerItem archiveAsFalse(
      Long intelligenceId, String title, String content, String contentHash,
      String reason, String matchedKeyword, String regionId, String industryId,
      String linkId, String sourceId, String archivedBy, String archiveReason) {
    FalseLedgerEntity entity = new FalseLedgerEntity();
    entity.setOriginalIntelligenceId(intelligenceId);
    entity.setTitle(title);
    entity.setContent(content);
    entity.setContentHash(contentHash);
    entity.setConflictReason(reason);
    entity.setMatchedRumorKeyword(matchedKeyword);
    entity.setSourceId(sourceId);
    entity.setRegionId(regionId);
    entity.setIndustryId(industryId);
    entity.setLinkId(linkId);
    entity.setArchivedBy(archivedBy);
    entity.setArchiveReason(archiveReason);
    falseLedgerMapper.insert(entity);
    return findFalseLedgerItem(entity.getId());
  }

  public List<ConflictResolutionRecord> listConflicts(String regionId, String industryId, int limit) {
    return conflictResolutionMapper.selectList(new LambdaQueryWrapper<ConflictResolutionEntity>()
        .eq(ConflictResolutionEntity::getRegionId, regionId)
        .eq(ConflictResolutionEntity::getIndustryId, industryId)
        .orderByDesc(ConflictResolutionEntity::getId)
        .last("limit " + limit)).stream().map(this::toConflictRecord).toList();
  }

  public List<FalseLedgerItem> listFalseLedger(String regionId, String industryId, int limit) {
    return falseLedgerMapper.selectList(new LambdaQueryWrapper<FalseLedgerEntity>()
        .eq(FalseLedgerEntity::getRegionId, regionId)
        .eq(FalseLedgerEntity::getIndustryId, industryId)
        .orderByDesc(FalseLedgerEntity::getId)
        .last("limit " + limit)).stream().map(this::toFalseLedgerItem).toList();
  }

  public boolean isContentInFalseLedger(String contentHash) {
    return falseLedgerMapper.selectCount(new LambdaQueryWrapper<FalseLedgerEntity>()
        .eq(FalseLedgerEntity::getContentHash, contentHash)) > 0;
  }

  private ConflictResolutionRecord findResolution(long id) {
    ConflictResolutionEntity entity = conflictResolutionMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("conflict resolution not found: " + id);
    }
    return toConflictRecord(entity);
  }

  private FalseLedgerItem findFalseLedgerItem(long id) {
    FalseLedgerEntity entity = falseLedgerMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("false ledger item not found: " + id);
    }
    return toFalseLedgerItem(entity);
  }

  private ConflictResolutionRecord toConflictRecord(ConflictResolutionEntity entity) {
    return new ConflictResolutionRecord(
        entity.getId(),
        entity.getIncomingIntelligenceId(),
        entity.getExistingIntelligenceId(),
        entity.getConflictBranch(),
        entity.getSimHashDistance() == null ? 0 : entity.getSimHashDistance(),
        entity.getIncomingWeight() == null ? 0D : entity.getIncomingWeight(),
        entity.getExistingWeight() == null ? 0D : entity.getExistingWeight(),
        entity.getRoutingAction(),
        entity.getReviewTicketId(),
        entity.getResolvedBy(),
        entity.getNotes(),
        entity.getRegionId(),
        entity.getIndustryId(),
        entity.getLinkId(),
        null);
  }

  private FalseLedgerItem toFalseLedgerItem(FalseLedgerEntity entity) {
    return new FalseLedgerItem(
        entity.getId(),
        entity.getOriginalIntelligenceId(),
        entity.getTitle(),
        entity.getContentHash(),
        entity.getConflictReason(),
        entity.getMatchedRumorKeyword(),
        entity.getRegionId(),
        entity.getIndustryId(),
        entity.getArchivedBy(),
        null);
  }
}
