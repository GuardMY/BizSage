package com.bizsage.api.admin;

import com.bizsage.api.admin.AdminCollectionDtos.CollectionDeadLetter;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionJobRun;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionKeyword;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionKeywordUpsertRequest;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionSourceConfig;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionSourceDetail;
import com.bizsage.api.admin.AdminCollectionDtos.CollectionSourceUpsertRequest;
import com.bizsage.api.intelligence.AdminReviewStore;
import com.bizsage.api.intelligence.IntelligenceItem;
import com.bizsage.api.intelligence.IntelligenceStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminCollectionStore {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };
  private static final Logger log = LoggerFactory.getLogger(AdminCollectionStore.class);

  private final AdminCollectionMapper mapper;
  private final CollectorClient collectorClient;
  private final ObjectMapper objectMapper;
  private final IntelligenceStore intelligenceStore;
  private final AdminReviewStore reviewStore;

  public AdminCollectionStore(
      AdminCollectionMapper mapper,
      CollectorClient collectorClient,
      ObjectMapper objectMapper,
      IntelligenceStore intelligenceStore,
      AdminReviewStore reviewStore) {
    this.mapper = mapper;
    this.collectorClient = collectorClient;
    this.objectMapper = objectMapper;
    this.intelligenceStore = intelligenceStore;
    this.reviewStore = reviewStore;
  }

  List<CollectionSourceConfig> listSources() {
    return mapper.listSources().stream().map(this::toSource).toList();
  }

  CollectionSourceDetail sourceDetail(long sourceConfigId) {
    CollectionSourceConfig source = findSource(sourceConfigId);
    return new CollectionSourceDetail(source, listKeywords(sourceConfigId), recentRuns(sourceConfigId, 12));
  }

  CollectionSourceDetail saveSource(CollectionSourceUpsertRequest request, String actor) {
    long sourceConfigId = request.sourceConfigId() == null ? createSource(request) : updateSource(request);
    CollectionSourceConfig source = findSource(sourceConfigId);
    writeAudit(actor, request.sourceConfigId() == null ? "ADMIN_COLLECTION_CREATE_SOURCE" : "ADMIN_COLLECTION_UPDATE_SOURCE", "collection_source", String.valueOf(sourceConfigId), "SUCCESS", source.regionId(), source.industryId());
    return sourceDetail(sourceConfigId);
  }

  List<CollectionKeyword> listKeywords(Long sourceConfigId) {
    return mapper.listKeywords(sourceConfigId).stream().map(this::toKeyword).toList();
  }

  CollectionKeyword saveKeyword(CollectionKeywordUpsertRequest request, String actor) {
    long sourceConfigId = request.sourceConfigId() == null ? 0L : request.sourceConfigId();
    if (sourceConfigId <= 0) {
      throw new IllegalArgumentException("source config id is required");
    }
    findSource(sourceConfigId);
    long keywordId = request.keywordId() == null ? createKeyword(request) : updateKeyword(request);
    CollectionKeyword keyword = findKeyword(keywordId);
    CollectionSourceConfig source = findSource(sourceConfigId);
    writeAudit(actor, request.keywordId() == null ? "ADMIN_COLLECTION_CREATE_KEYWORD" : "ADMIN_COLLECTION_UPDATE_KEYWORD", "collection_keyword", String.valueOf(keywordId), "SUCCESS", source.regionId(), source.industryId());
    return keyword;
  }

  List<CollectionJobRun> listRuns(String status) {
    String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
    return mapper.listRuns(normalized).stream().map(this::toRun).toList();
  }

  List<CollectionDeadLetter> listDeadLetters() {
    return mapper.listDeadLetters().stream().map(this::toDeadLetter).toList();
  }

  CollectionJobRun runNow(long sourceConfigId, String actor) {
    return executeSource(sourceConfigId, "MANUAL", actor, false);
  }

  void runDueSources() {
    for (CollectionSourceConfig source : dueSources()) {
      try {
        executeSource(source.sourceConfigId(), "SCHEDULED", "system", true);
      } catch (Exception ignored) {
      }
    }
  }

  private List<CollectionSourceConfig> dueSources() {
    LocalDateTime now = LocalDateTime.now();
    List<CollectionSourceConfig> sources = mapper.listDueSources(now).stream().map(this::toSource).toList();
    List<CollectionSourceConfig> due = new ArrayList<>();
    for (CollectionSourceConfig source : sources) {
      if ("OPEN".equals(source.circuitState()) && source.nextRunTime() != null && source.nextRunTime().isAfter(now)) {
        continue;
      }
      if (hasRunningRun(source.sourceConfigId())) {
        continue;
      }
      due.add(source);
    }
    return due;
  }

  private boolean hasRunningRun(long sourceConfigId) {
    return intValue(mapper.countRunningRuns(sourceConfigId)) > 0;
  }

  private CollectionJobRun executeSource(long sourceConfigId, String triggerType, String actor, boolean scheduled) {
    CollectionSourceConfig source = findSource(sourceConfigId);
    if (!"ENABLED".equals(source.status()) && scheduled) {
      throw new IllegalArgumentException("source is not enabled");
    }
    if ("OPEN".equals(source.circuitState()) && source.nextRunTime() != null && source.nextRunTime().isAfter(LocalDateTime.now())) {
      throw new IllegalArgumentException("source circuit is open");
    }

    long jobId = createCollectionJob(source);
    long runId = createCollectionRun(source, triggerType, jobId);
    Map<String, Object> payload = payloadForSource(source);
    List<CollectionKeyword> keywords = listKeywords(sourceConfigId).stream().filter(keyword -> "ACTIVE".equals(keyword.status())).toList();

    int attempt = 0;
    int maxAttempts = Math.max(1, source.maxRetries() == null ? 1 : source.maxRetries() + 1);
    Exception failure = null;
    while (attempt < maxAttempts) {
      attempt += 1;
      try {
        List<Map<String, Object>> records = collectorClient.collectAndGovern(source.sourceType(), payload);
        List<Map<String, Object>> filtered = applyKeywords(records, keywords);
        int persisted = persistRawRecords(filtered, source, jobId, keywords);

        if (!filtered.isEmpty()) {
          List<IntelligenceItem> intelItems = intelligenceStore.bulkCreateFromRecords(filtered);
          int ticketsCreated = reviewStore.createTickets(intelItems);
          if (!intelItems.isEmpty()) {
            log.info("Collection auto-created {} intelligence items and {} review tickets (source={}, job={})", intelItems.size(), ticketsCreated, source.name(), jobId);
          }
        }

        markRunSuccess(source, runId, jobId, records.size(), records.size() - filtered.size(), persisted, attempt - 1);
        writeAudit(actor, "ADMIN_COLLECTION_RUN_SOURCE", "collection_source", String.valueOf(sourceConfigId), "SUCCESS", source.regionId(), source.industryId());
        return findRun(runId);
      } catch (Exception exception) {
        failure = exception;
      }
    }

    markRunFailure(source, runId, jobId, attempt - 1, failure == null ? "unknown collection failure" : failure.getMessage(), payload);
    writeAudit(actor, "ADMIN_COLLECTION_RUN_SOURCE", "collection_source", String.valueOf(sourceConfigId), "FAILED", source.regionId(), source.industryId());
    return findRun(runId);
  }

  private long createSource(CollectionSourceUpsertRequest request) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("name", blankToDefault(request.name(), "Untitled source"));
    values.put("sourceType", normalizeSourceType(request.sourceType()));
    values.put("status", blankToDefault(request.status(), "ENABLED"));
    values.put("intervalMinutes", positiveOrDefault(request.intervalMinutes(), 30));
    values.put("maxRetries", positiveOrDefault(request.maxRetries(), 1));
    values.put("failureThreshold", positiveOrDefault(request.failureThreshold(), 3));
    values.put("cooldownMinutes", positiveOrDefault(request.cooldownMinutes(), 30));
    values.put("regionId", blankToDefault(request.regionId(), "cn-default"));
    values.put("industryId", blankToDefault(request.industryId(), "general"));
    values.put("linkId", blankToDefault(request.linkId(), "collection"));
    values.put("sourceId", blankToDefault(request.sourceId(), "admin-collector"));
    values.put("payloadJson", blankJson(request.payloadJson()));
    values.put("nextRunTime", LocalDateTime.now());
    mapper.insertSource(values);
    return longValue(values.get("id"));
  }

  private long updateSource(CollectionSourceUpsertRequest request) {
    long sourceConfigId = request.sourceConfigId() == null ? 0L : request.sourceConfigId();
    CollectionSourceConfig existing = findSource(sourceConfigId);
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("sourceConfigId", sourceConfigId);
    values.put("name", blankToDefault(request.name(), existing.name()));
    values.put("sourceType", normalizeSourceType(blankToDefault(request.sourceType(), existing.sourceType())));
    values.put("status", blankToDefault(request.status(), existing.status()));
    values.put("intervalMinutes", positiveOrDefault(request.intervalMinutes(), existing.intervalMinutes()));
    values.put("maxRetries", positiveOrDefault(request.maxRetries(), existing.maxRetries()));
    values.put("failureThreshold", positiveOrDefault(request.failureThreshold(), existing.failureThreshold()));
    values.put("cooldownMinutes", positiveOrDefault(request.cooldownMinutes(), existing.cooldownMinutes()));
    values.put("regionId", blankToDefault(request.regionId(), existing.regionId()));
    values.put("industryId", blankToDefault(request.industryId(), existing.industryId()));
    values.put("linkId", blankToDefault(request.linkId(), existing.linkId()));
    values.put("sourceId", blankToDefault(request.sourceId(), existing.sourceId()));
    values.put("payloadJson", request.payloadJson() == null ? existing.payloadJson() : blankJson(request.payloadJson()));
    values.put("nextRunTime", nextRunTimeForStatus(blankToDefault(request.status(), existing.status()), positiveOrDefault(request.intervalMinutes(), existing.intervalMinutes())));
    mapper.updateSource(values);
    return sourceConfigId;
  }

  private long createKeyword(CollectionKeywordUpsertRequest request) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("sourceConfigId", request.sourceConfigId());
    values.put("keyword", blankToDefault(request.keyword(), "keyword"));
    values.put("matchMode", normalizeMatchMode(request.matchMode()));
    values.put("status", blankToDefault(request.status(), "ACTIVE"));
    values.put("notes", request.notes());
    mapper.insertKeyword(values);
    return longValue(values.get("id"));
  }

  private long updateKeyword(CollectionKeywordUpsertRequest request) {
    long keywordId = request.keywordId() == null ? 0L : request.keywordId();
    CollectionKeyword existing = findKeyword(keywordId);
    mapper.updateKeyword(Map.of(
        "keywordId", keywordId,
        "keyword", blankToDefault(request.keyword(), existing.keyword()),
        "matchMode", normalizeMatchMode(blankToDefault(request.matchMode(), existing.matchMode())),
        "status", blankToDefault(request.status(), existing.status()),
        "notes", request.notes() == null ? existing.notes() : request.notes()));
    return keywordId;
  }

  private long createCollectionJob(CollectionSourceConfig source) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("sourceType", source.sourceType());
    values.put("circuitState", source.circuitState());
    values.put("sourceId", source.sourceId());
    values.put("regionId", source.regionId());
    values.put("industryId", source.industryId());
    mapper.insertCollectionJob(values);
    return longValue(values.get("id"));
  }

  private long createCollectionRun(CollectionSourceConfig source, String triggerType, long jobId) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("jobId", jobId);
    values.put("sourceConfigId", source.sourceConfigId());
    values.put("sourceType", source.sourceType());
    values.put("triggerType", triggerType);
    mapper.insertCollectionRun(values);
    return longValue(values.get("id"));
  }

  private void markRunSuccess(CollectionSourceConfig source, long runId, long jobId, int recordsCollected, int recordsFiltered, int recordsPersisted, int retryCount) {
    LocalDateTime now = LocalDateTime.now();
    mapper.markCollectionJobSuccess(jobId, recordsCollected, retryCount);
    mapper.markRunSuccess(runId, recordsCollected, recordsFiltered, recordsPersisted);
    mapper.markSourceSuccess(source.sourceConfigId(), now, now.plusMinutes(Math.max(1, source.intervalMinutes())));
  }

  private void markRunFailure(CollectionSourceConfig source, long runId, long jobId, int retryCount, String errorMessage, Map<String, Object> payload) {
    int failureCount = (source.failureCount() == null ? 0 : source.failureCount()) + 1;
    boolean openCircuit = failureCount >= Math.max(1, source.failureThreshold());
    String jobStatus = openCircuit ? "DEAD_LETTER" : "FAILED";
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextRun = openCircuit ? now.plusMinutes(Math.max(1, source.cooldownMinutes())) : now.plusMinutes(Math.max(1, source.intervalMinutes()));
    mapper.markCollectionJobFailure(jobId, jobStatus, retryCount, openCircuit ? "OPEN" : "CLOSED");
    mapper.markRunFailure(runId, jobStatus, errorMessage);
    mapper.markSourceFailure(source.sourceConfigId(), openCircuit ? "OPEN" : "CLOSED", failureCount, now, nextRun, jobStatus, errorMessage);
    mapper.insertDeadLetter(Map.of(
        "jobId", jobId,
        "reason", openCircuit ? "CIRCUIT_OPEN" : "COLLECTOR_FAILURE",
        "errorMessage", errorMessage,
        "payloadJson", json(payload),
        "sourceId", source.sourceId(),
        "regionId", source.regionId(),
        "industryId", source.industryId()));
  }

  private int persistRawRecords(List<Map<String, Object>> records, CollectionSourceConfig source, long jobId, List<CollectionKeyword> keywords) {
    int persisted = 0;
    for (Map<String, Object> record : records) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("sourceType", blankToDefault(stringValue(record.get("source_type")), source.sourceType()));
      values.put("title", blankToDefault(stringValue(record.get("title")), source.name()));
      values.put("content", blankToDefault(stringValue(record.get("content")), ""));
      values.put("url", stringValue(record.get("url")));
      values.put("confidence", decimalValue(record.get("confidence"), BigDecimal.valueOf(0.6)));
      values.put("linkId", blankToDefault(stringValue(record.get("link_id")), source.linkId()));
      values.put("regionId", blankToDefault(stringValue(record.get("region_id")), source.regionId()));
      values.put("industryId", blankToDefault(stringValue(record.get("industry_id")), source.industryId()));
      values.put("sourceId", blankToDefault(stringValue(record.get("source_id")), source.sourceId()));
      values.put("weight", decimalValue(record.get("weight"), BigDecimal.valueOf(0.6)));
      values.put("metadataJson", json(Map.of(
          "sourceConfigId", source.sourceConfigId(),
          "collectionJobId", jobId,
          "appliedKeywords", keywords.stream().map(CollectionKeyword::keyword).toList())));
      mapper.insertRawRecord(values);
      persisted += 1;
    }
    return persisted;
  }

  private List<Map<String, Object>> applyKeywords(List<Map<String, Object>> records, List<CollectionKeyword> keywords) {
    List<String> includes = keywords.stream().filter(keyword -> "INCLUDE".equals(keyword.matchMode())).map(CollectionKeyword::keyword).map(this::lowercase).toList();
    List<String> excludes = keywords.stream().filter(keyword -> "EXCLUDE".equals(keyword.matchMode())).map(CollectionKeyword::keyword).map(this::lowercase).toList();
    List<Map<String, Object>> filtered = new ArrayList<>();
    for (Map<String, Object> record : records) {
      String haystack = lowercase(stringValue(record.get("title")) + " " + stringValue(record.get("content")));
      boolean includeMatch = includes.isEmpty() || includes.stream().anyMatch(haystack::contains);
      boolean excludeMatch = excludes.stream().anyMatch(haystack::contains);
      if (includeMatch && !excludeMatch) {
        filtered.add(record);
      }
    }
    return filtered;
  }

  private List<CollectionJobRun> recentRuns(long sourceConfigId, int limit) {
    return mapper.recentRuns(sourceConfigId, limit).stream().map(this::toRun).toList();
  }

  private CollectionSourceConfig findSource(long sourceConfigId) {
    return toSource(requireMap(mapper.findSource(sourceConfigId), "collection source not found"));
  }

  private CollectionKeyword findKeyword(long keywordId) {
    return toKeyword(requireMap(mapper.findKeyword(keywordId), "collection keyword not found"));
  }

  private CollectionJobRun findRun(long runId) {
    return toRun(requireMap(mapper.findRun(runId), "collection run not found"));
  }

  private Map<String, Object> payloadForSource(CollectionSourceConfig source) {
    try {
      Map<String, Object> payload = StringUtils.hasText(source.payloadJson()) ? objectMapper.readValue(source.payloadJson(), MAP_TYPE) : new LinkedHashMap<>();
      payload.putIfAbsent("region_id", source.regionId());
      payload.putIfAbsent("industry_id", source.industryId());
      payload.putIfAbsent("link_id", source.linkId());
      payload.putIfAbsent("source_id", source.sourceId());
      return payload;
    } catch (IOException exception) {
      throw new IllegalArgumentException("invalid source payload json");
    }
  }

  private String normalizeSourceType(String sourceType) {
    String normalized = blankToDefault(sourceType, "PUBLIC_PAGE").trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if (!List.of("PUBLIC_PAGE", "MOCK_API", "FORM").contains(normalized)) {
      throw new IllegalArgumentException("unsupported source type");
    }
    return normalized;
  }

  private String normalizeMatchMode(String matchMode) {
    String normalized = blankToDefault(matchMode, "INCLUDE").trim().toUpperCase(Locale.ROOT);
    if (!List.of("INCLUDE", "EXCLUDE").contains(normalized)) {
      throw new IllegalArgumentException("unsupported keyword match mode");
    }
    return normalized;
  }

  private LocalDateTime nextRunTimeForStatus(String status, Integer intervalMinutes) {
    if (!"ENABLED".equals(blankToDefault(status, "ENABLED").trim().toUpperCase(Locale.ROOT))) {
      return LocalDateTime.now().plusYears(10);
    }
    return LocalDateTime.now().plusMinutes(Math.max(1, intervalMinutes == null ? 30 : intervalMinutes));
  }

  private String blankJson(String json) {
    if (!StringUtils.hasText(json)) {
      return "{}";
    }
    try {
      objectMapper.readTree(json);
      return json;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("invalid json payload");
    }
  }

  private BigDecimal decimalValue(Object value, BigDecimal fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private int positiveOrDefault(Integer value, Integer fallback) {
    int candidate = value == null ? (fallback == null ? 1 : fallback) : value;
    return Math.max(1, candidate);
  }

  private String blankToDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Clob clob) {
      try {
        return clob.getSubString(1, (int) clob.length());
      } catch (SQLException exception) {
        throw new IllegalArgumentException("unable to read clob value", exception);
      }
    }
    return String.valueOf(value);
  }

  private String lowercase(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("unable to serialize json");
    }
  }

  private void writeAudit(String actor, String action, String targetType, String targetId, String result, String regionId, String industryId) {
    mapper.insertAuditLog(Map.of(
        "actor", actor,
        "action", action,
        "targetType", targetType,
        "targetId", targetId,
        "result", result,
        "regionId", regionId,
        "industryId", industryId));
  }

  private CollectionSourceConfig toSource(Map<String, Object> row) {
    return new CollectionSourceConfig(
        longValue(row, "sourceConfigId"),
        stringValue(row, "name"),
        stringValue(row, "sourceType"),
        stringValue(row, "status"),
        intObj(row, "intervalMinutes"),
        intObj(row, "maxRetries"),
        intObj(row, "failureThreshold"),
        intObj(row, "cooldownMinutes"),
        stringValue(row, "circuitState"),
        intObj(row, "failureCount"),
        stringValue(row, "regionId"),
        stringValue(row, "industryId"),
        stringValue(row, "linkId"),
        stringValue(row, "sourceId"),
        stringValue(row, "payloadJson"),
        timeValue(row, "nextRunTime"),
        timeValue(row, "lastRunTime"),
        stringValue(row, "lastStatus"),
        stringValue(row, "lastError"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private CollectionKeyword toKeyword(Map<String, Object> row) {
    return new CollectionKeyword(
        longValue(row, "keywordId"),
        longValue(row, "sourceConfigId"),
        stringValue(row, "keyword"),
        stringValue(row, "matchMode"),
        stringValue(row, "status"),
        stringValue(row, "notes"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private CollectionJobRun toRun(Map<String, Object> row) {
    return new CollectionJobRun(
        longValue(row, "runId"),
        longValue(row, "jobId"),
        longValue(row, "sourceConfigId"),
        stringValue(row, "sourceName"),
        stringValue(row, "sourceType"),
        stringValue(row, "triggerType"),
        stringValue(row, "status"),
        intObj(row, "queueDepth"),
        intObj(row, "retryCount"),
        stringValue(row, "circuitState"),
        intObj(row, "recordsCollected"),
        intObj(row, "recordsFiltered"),
        intObj(row, "recordsPersisted"),
        stringValue(row, "errorMessage"),
        timeValue(row, "startTime"),
        timeValue(row, "finishTime"));
  }

  private CollectionDeadLetter toDeadLetter(Map<String, Object> row) {
    return new CollectionDeadLetter(
        longValue(row, "deadLetterId"),
        nullableLong(row, "jobId"),
        stringValue(row, "sourceName"),
        stringValue(row, "reason"),
        stringValue(row, "errorMessage"),
        stringValue(row, "payloadJson"),
        timeValue(row, "createTime"));
  }

  private Map<String, Object> requireMap(Map<String, Object> row, String message) {
    if (row == null || row.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return row;
  }

  private int intValue(Integer value) {
    return value == null ? 0 : value;
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

  private Integer intObj(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof Number number ? number.intValue() : null;
  }

  private long longValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private Long nullableLong(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof Number number ? number.longValue() : null;
  }

  private LocalDateTime timeValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof LocalDateTime time ? time : null;
  }

  private String stringValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return stringValue(value);
  }
}
