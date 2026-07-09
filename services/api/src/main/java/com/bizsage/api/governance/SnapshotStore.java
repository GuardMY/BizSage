package com.bizsage.api.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bizsage.api.governance.SnapshotDtos.SnapshotCompareResult;
import com.bizsage.api.governance.SnapshotDtos.SnapshotDetail;
import com.bizsage.api.governance.SnapshotDtos.SnapshotSummary;
import com.bizsage.api.governance.SnapshotDtos.SnapshotType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SnapshotStore {
  private final SnapshotMapper snapshotMapper;
  private final SnapshotQueryMapper snapshotQueryMapper;
  private final ObjectMapper objectMapper;

  public SnapshotStore(SnapshotMapper snapshotMapper, SnapshotQueryMapper snapshotQueryMapper, ObjectMapper objectMapper) {
    this.snapshotMapper = snapshotMapper;
    this.snapshotQueryMapper = snapshotQueryMapper;
    this.objectMapper = objectMapper;
  }

  public SnapshotDetail create(SnapshotType type, String scopeKey, String payloadJson,
      String regionId, String industryId, int retentionDays, int recordCount) {
    Long parentId = findLatestByTypeAndScope(type, regionId, industryId).map(SnapshotSummary::id).orElse(null);
    SnapshotEntity entity = new SnapshotEntity();
    entity.setSnapshotType(type.name());
    entity.setScopeKey(scopeKey);
    entity.setPayloadJson(payloadJson);
    entity.setSourceId("snapshot");
    entity.setWeight(1.0D);
    entity.setRegionId(regionId);
    entity.setIndustryId(industryId);
    entity.setRetentionDays(retentionDays);
    entity.setRecordCount(recordCount);
    entity.setParentSnapshotId(parentId);
    entity.setExpiresAt(LocalDateTime.now().plusDays(retentionDays));
    snapshotMapper.insert(entity);
    return find(entity.getId());
  }

  public SnapshotDetail find(long id) {
    SnapshotEntity entity = snapshotMapper.selectById(id);
    if (entity == null) {
      throw new IllegalArgumentException("snapshot not found: " + id);
    }
    return toDetail(entity);
  }

  public List<SnapshotSummary> listByScope(SnapshotType type, String regionId, String industryId, int limit) {
    return snapshotMapper.selectList(new LambdaQueryWrapper<SnapshotEntity>()
        .eq(SnapshotEntity::getSnapshotType, type.name())
        .eq(SnapshotEntity::getRegionId, regionId)
        .eq(SnapshotEntity::getIndustryId, industryId)
        .orderByDesc(SnapshotEntity::getCreateTime)
        .last("limit " + limit)).stream().map(this::toSummary).toList();
  }

  public Optional<SnapshotSummary> findLatestByTypeAndScope(SnapshotType type, String regionId, String industryId) {
    return snapshotMapper.selectList(new LambdaQueryWrapper<SnapshotEntity>()
        .eq(SnapshotEntity::getSnapshotType, type.name())
        .eq(SnapshotEntity::getRegionId, regionId)
        .eq(SnapshotEntity::getIndustryId, industryId)
        .orderByDesc(SnapshotEntity::getCreateTime)
        .last("limit 1")).stream().findFirst().map(this::toSummary);
  }

  public SnapshotCompareResult compare(long leftId, long rightId) {
    SnapshotDetail left = find(leftId);
    SnapshotDetail right = find(rightId);
    List<Map<String, Object>> leftRecords = parsePayload(left.payloadJson());
    List<Map<String, Object>> rightRecords = parsePayload(right.payloadJson());
    Set<String> leftKeys = extractKeys(leftRecords);
    Set<String> rightKeys = extractKeys(rightRecords);
    List<String> added = new ArrayList<>(rightKeys);
    added.removeAll(leftKeys);
    List<String> removed = new ArrayList<>(leftKeys);
    removed.removeAll(rightKeys);
    Set<String> common = new HashSet<>(leftKeys);
    common.retainAll(rightKeys);
    List<String> changed = new ArrayList<>();
    for (String key : common) {
      Map<String, Object> lr = findByKey(leftRecords, key);
      Map<String, Object> rr = findByKey(rightRecords, key);
      if (lr != null && rr != null) {
        String lc = String.valueOf(lr.getOrDefault("confidence", ""));
        String rc = String.valueOf(rr.getOrDefault("confidence", ""));
        String ls = String.valueOf(lr.getOrDefault("status", ""));
        String rs = String.valueOf(rr.getOrDefault("status", ""));
        if (!lc.equals(rc) || !ls.equals(rs)) {
          changed.add(key);
        }
      }
    }
    return new SnapshotCompareResult(leftId, rightId, added, removed, changed);
  }

  public int deleteExpired() {
    return snapshotMapper.delete(new LambdaQueryWrapper<SnapshotEntity>()
        .isNotNull(SnapshotEntity::getExpiresAt)
        .le(SnapshotEntity::getExpiresAt, LocalDateTime.now()));
  }

  public List<String> distinctScopes() {
    return snapshotQueryMapper.distinctScopes();
  }

  private SnapshotSummary toSummary(SnapshotEntity entity) {
    return new SnapshotSummary(entity.getId(), entity.getSnapshotType(), entity.getScopeKey(),
        entity.getRegionId(), entity.getIndustryId(), entity.getRetentionDays() == null ? 0 : entity.getRetentionDays(),
        entity.getRecordCount() == null ? 0 : entity.getRecordCount(), entity.getExpiresAt(), entity.getCreateTime());
  }

  private SnapshotDetail toDetail(SnapshotEntity entity) {
    return new SnapshotDetail(entity.getId(), entity.getSnapshotType(), entity.getScopeKey(), entity.getPayloadJson(),
        entity.getRegionId(), entity.getIndustryId(), entity.getRetentionDays() == null ? 0 : entity.getRetentionDays(),
        entity.getRecordCount() == null ? 0 : entity.getRecordCount(), entity.getParentSnapshotId(), entity.getExpiresAt(), entity.getCreateTime());
  }

  private String extractKey(Map<String, Object> record) {
    return String.format("%s|%s|%s", record.getOrDefault("title", ""), record.getOrDefault("link_id", ""), record.getOrDefault("source_id", ""));
  }

  private Set<String> extractKeys(List<Map<String, Object>> records) {
    Set<String> keys = new HashSet<>();
    for (Map<String, Object> record : records) {
      keys.add(extractKey(record));
    }
    return keys;
  }

  private Map<String, Object> findByKey(List<Map<String, Object>> records, String key) {
    return records.stream().filter(record -> extractKey(record).equals(key)).findFirst().orElse(null);
  }

  private List<Map<String, Object>> parsePayload(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }
}
