package com.bizsage.api.governance;

import com.bizsage.api.governance.SnapshotDtos.SnapshotDetail;
import com.bizsage.api.governance.SnapshotDtos.SnapshotRetentionPolicy;
import com.bizsage.api.governance.SnapshotDtos.SnapshotType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 生成情报状态的时间序列快照。
 *
 * <ul>
 *   <li>DAILY：按 scope 保存 APPROVED 情报明细。</li>
 *   <li>WEEKLY：按 link_id 聚合数量、平均置信度和权重。</li>
 *   <li>MONTHLY：在周聚合基础上增加近 30 天新增量，用于趋势判断。</li>
 * </ul>
 */
@Service
public class SnapshotService {

  private final SnapshotStore store;
  private final ObjectMapper objectMapper;
  private final SnapshotQueryMapper snapshotQueryMapper;

  public SnapshotService(SnapshotStore store, ObjectMapper objectMapper, SnapshotQueryMapper snapshotQueryMapper) {
    this.store = store;
    this.objectMapper = objectMapper;
    this.snapshotQueryMapper = snapshotQueryMapper;
  }

  public SnapshotDetail generateSnapshot(SnapshotType type, String regionId, String industryId) {
    // payload 由快照类型决定，保留 JSON 字符串便于审计和后续离线分析。
    String payloadJson = switch (type) {
      case DAILY -> buildDailyPayload(regionId, industryId);
      case WEEKLY -> buildWeeklyPayload(regionId, industryId);
      case MONTHLY -> buildMonthlyPayload(regionId, industryId);
    };

    int recordCount = countRecords(payloadJson);
    int retentionDays = SnapshotRetentionPolicy.DEFAULT.forType(type);
    String scopeKey = String.format("%s|%s", regionId, industryId);

    return store.create(type, scopeKey, payloadJson, regionId, industryId,
        retentionDays, recordCount);
  }

  private String buildDailyPayload(String regionId, String industryId) {
    return toJson(snapshotQueryMapper.listApprovedIntelligenceRecords(regionId, industryId));
  }

  private String buildWeeklyPayload(String regionId, String industryId) {
    return toJson(snapshotQueryMapper.listWeeklySnapshotAggregates(regionId, industryId));
  }

  private String buildMonthlyPayload(String regionId, String industryId) {
    return toJson(snapshotQueryMapper.listMonthlySnapshotAggregates(regionId, industryId));
  }

  public void generateAllScopes(SnapshotType type) {
    // 对当前已有 scope 逐个生成快照；空库时仍生成默认 scope，保证调度可观测。
    List<String> scopes = store.distinctScopes();
    if (scopes.isEmpty()) {
      // 空库时也保留一条默认快照，方便验证调度链路。
      store.create(type, "cn-default|general", "[]",
          "cn-default", "general", SnapshotRetentionPolicy.DEFAULT.forType(type), 0);
      return;
    }
    for (String scope : scopes) {
      String[] parts = scope.split("\\|", 2);
      if (parts.length == 2) {
        generateSnapshot(type, parts[0], parts[1]);
      }
    }
  }

  private int countRecords(String json) {
    // 快照 payload 约定为数组，解析失败时保守记为 0。
    try {
      List<?> list = objectMapper.readValue(json, List.class);
      return list.size();
    } catch (JsonProcessingException e) {
      return 0;
    }
  }

  private String toJson(Object value) {
    // 快照生成失败时返回空数组，避免调度任务因单个 scope 中断。
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}
