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
 * Generates time-series snapshots of intelligence state.
 *
 * <ul>
 *   <li>DAILY: full dump of all APPROVED intelligence for each scope.</li>
 *   <li>WEEKLY: aggregated summary grouped by link_id with counts and average confidence.</li>
 *   <li>MONTHLY: extended weekly data plus trend comparison against the previous month.</li>
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
    List<Map<String, Object>> records = jdbcTemplate.queryForList(
        "select id, title, content, status, confidence, link_id, region_id, industry_id, "
            + "source_id, weight, url from intelligence "
            + "where status = 'APPROVED' and region_id = ? and industry_id = ?",
        regionId, industryId);
    return toJson(records);
  }

  private String buildWeeklyPayload(String regionId, String industryId) {
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
    List<String> scopes = store.distinctScopes();
    if (scopes.isEmpty()) {
      // Generate at least one snapshot per type for the default scope
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
    try {
      List<?> list = objectMapper.readValue(json, List.class);
      return list.size();
    } catch (JsonProcessingException e) {
      return 0;
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}
