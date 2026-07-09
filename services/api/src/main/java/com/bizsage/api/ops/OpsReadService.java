package com.bizsage.api.ops;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpsReadService {
  private final OpsQueryMapper opsQueryMapper;

  public OpsReadService(OpsQueryMapper opsQueryMapper) {
    this.opsQueryMapper = opsQueryMapper;
  }

  public List<Map<String, Object>> listAlerts() {
    return opsQueryMapper.listAlerts().stream().map(row -> normalize(row, "level", "scope", "status", "owner", "message")).toList();
  }

  public List<Map<String, Object>> listReviewWorkOrders() {
    return opsQueryMapper.listReviewWorkOrders().stream().map(row -> normalize(row, "id", "reason", "status", "sourceId", "targetType", "targetId")).toList();
  }

  public List<Map<String, Object>> listAuditLogs() {
    return opsQueryMapper.listAuditLogs().stream().map(row -> normalize(row, "id", "action", "actor", "result", "targetType", "targetId")).toList();
  }

  private Map<String, Object> normalize(Map<String, Object> row, String... keys) {
    java.util.Map<String, Object> normalized = new java.util.LinkedHashMap<>();
    for (String key : keys) {
      normalized.put(key, lookup(row, key));
    }
    return normalized;
  }

  private Object lookup(Map<String, Object> row, String key) {
    if (row.containsKey(key)) {
      return row.get(key);
    }
    for (Map.Entry<String, Object> entry : row.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
