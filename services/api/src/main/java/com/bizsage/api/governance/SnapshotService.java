package com.bizsage.api.governance;

import com.bizsage.api.governance.SnapshotDtos.SnapshotDetail;
import com.bizsage.api.governance.SnapshotDtos.SnapshotRetentionPolicy;
import com.bizsage.api.governance.SnapshotDtos.SnapshotType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
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
  private final JdbcTemplate jdbcTemplate;

  public SnapshotService(SnapshotStore store, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
    this.store = store;
    this.objectMapper = objectMapper;
    this.jdbcTemplate = jdbcTemplate;
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
    // 日快照保留明细，用于回溯某一地域/行业在当天的完整可用情报。
    List<Map<String, Object>> records = jdbcTemplate.queryForList(
        "select id, title, content, status, confidence, link_id, region_id, industry_id, "
            + "source_id, weight, url from intelligence "
            + "where status = 'APPROVED' and region_id = ? and industry_id = ?",
        regionId, industryId);
    return toJson(records);
  }

  private String buildWeeklyPayload(String regionId, String industryId) {
    // 周快照聚合到 link_id 维度，降低存储量并支持运营看板。
    List<Map<String, Object>> aggregated = jdbcTemplate.queryForList(
        "select link_id, count(*) as record_count, avg(confidence) as avg_confidence, "
            + "avg(weight) as avg_weight, max(create_time) as latest "
            + "from intelligence "
            + "where status = 'APPROVED' and region_id = ? and industry_id = ? "
            + "group by link_id",
        regionId, industryId);
    return toJson(aggregated);
  }

  private String buildMonthlyPayload(String regionId, String industryId) {
    // 月快照增加近 30 天新增量，方便识别哪些链条节点近期变化更活跃。
    List<Map<String, Object>> aggregated = jdbcTemplate.queryForList(
        "select link_id, count(*) as record_count, avg(confidence) as avg_confidence, "
            + "avg(weight) as avg_weight,"
            + "count(case when create_time >= date_sub(current_timestamp, interval 30 day) then 1 end) as new_this_month, "
            + "max(create_time) as latest "
            + "from intelligence "
            + "where status = 'APPROVED' and region_id = ? and industry_id = ? "
            + "group by link_id",
        regionId, industryId);
    return toJson(aggregated);
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
