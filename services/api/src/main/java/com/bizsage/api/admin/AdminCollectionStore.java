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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminCollectionStore {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };
  private static final Logger log = LoggerFactory.getLogger(AdminCollectionStore.class);

  private final JdbcTemplate jdbcTemplate;
  private final CollectorClient collectorClient;
  private final ObjectMapper objectMapper;
  private final IntelligenceStore intelligenceStore;
  private final AdminReviewStore reviewStore;

  public AdminCollectionStore(
      JdbcTemplate jdbcTemplate,
      CollectorClient collectorClient,
      ObjectMapper objectMapper,
      IntelligenceStore intelligenceStore,
      AdminReviewStore reviewStore) {
    this.jdbcTemplate = jdbcTemplate;
    this.collectorClient = collectorClient;
    this.objectMapper = objectMapper;
    this.intelligenceStore = intelligenceStore;
    this.reviewStore = reviewStore;
  }

  List<CollectionSourceConfig> listSources() {
    // 管理端列表按启用状态和最新创建排序，便于优先查看正在运行的源。
    return jdbcTemplate.query("""
        select id, name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
               circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json,
               next_run_time, last_run_time, last_status, last_error, create_time, update_time
          from admin_collection_sources
         order by status desc, id desc
        """, sourceMapper());
  }

  CollectionSourceDetail sourceDetail(long sourceConfigId) {
    // 详情页同时展示源配置、关键词和最近运行记录。
    CollectionSourceConfig source = findSource(sourceConfigId);
    return new CollectionSourceDetail(source, listKeywords(sourceConfigId), recentRuns(sourceConfigId, 12));
  }

  CollectionSourceDetail saveSource(CollectionSourceUpsertRequest request, String actor) {
    // 创建/更新采集源后写审计日志，保证管理端操作可追溯。
    long sourceConfigId = request.sourceConfigId() == null ? createSource(request) : updateSource(request);
    CollectionSourceConfig source = findSource(sourceConfigId);
    writeAudit(actor, request.sourceConfigId() == null ? "ADMIN_COLLECTION_CREATE_SOURCE" : "ADMIN_COLLECTION_UPDATE_SOURCE", "collection_source", String.valueOf(sourceConfigId), "SUCCESS", source.regionId(), source.industryId());
    return sourceDetail(sourceConfigId);
  }

  List<CollectionKeyword> listKeywords(Long sourceConfigId) {
    if (sourceConfigId == null) {
      return jdbcTemplate.query("""
          select id, source_config_id, keyword, match_mode, status, notes, create_time, update_time
            from admin_collection_keywords
           order by source_config_id asc, id desc
          """, keywordMapper());
    }
    return jdbcTemplate.query("""
        select id, source_config_id, keyword, match_mode, status, notes, create_time, update_time
          from admin_collection_keywords
         where source_config_id = ?
         order by id desc
        """, keywordMapper(), sourceConfigId);
  }

  CollectionKeyword saveKeyword(CollectionKeywordUpsertRequest request, String actor) {
    // 关键词必须绑定到已存在的采集源；INCLUDE/EXCLUDE 在执行阶段统一应用。
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
    String where = StringUtils.hasText(status) ? " where r.status = ?" : "";
    Object[] args = StringUtils.hasText(status) ? new Object[] {status.trim().toUpperCase(Locale.ROOT)} : new Object[0];
    return jdbcTemplate.query("""
        select r.id, r.job_id, r.source_config_id, s.name as source_name, r.source_type, r.trigger_type, r.status,
               j.queue_depth, j.retry_count, j.circuit_state, r.records_collected, r.records_filtered,
               r.records_persisted, r.error_message, r.start_time, r.finish_time
          from admin_collection_job_runs r
          left join collection_jobs j on j.id = r.job_id
          left join admin_collection_sources s on s.id = r.source_config_id
        """ + where + " order by r.id desc", runMapper(), args);
  }

  List<CollectionDeadLetter> listDeadLetters() {
    return jdbcTemplate.query("""
        select d.id, d.job_id, s.name as source_name, d.reason, d.error_message, cast(d.payload_json as char) as payload_json, d.create_time
          from dead_letter_records d
          left join admin_collection_job_runs r on r.job_id = d.job_id
          left join admin_collection_sources s on s.id = r.source_config_id
         order by d.id desc
        """, deadLetterMapper());
  }

  CollectionJobRun runNow(long sourceConfigId, String actor) {
    return executeSource(sourceConfigId, "MANUAL", actor, false);
  }

  void runDueSources() {
    for (CollectionSourceConfig source : dueSources()) {
      try {
        executeSource(source.sourceConfigId(), "SCHEDULED", "system", true);
      } catch (Exception ignored) {
        // 单个源失败不能阻断本轮调度，失败状态已在 executeSource 内记录。
      }
    }
  }

  private List<CollectionSourceConfig> dueSources() {
    // 找出到期、启用且未运行中的采集源；熔断冷却期内的源会被跳过。
    LocalDateTime now = LocalDateTime.now();
    List<CollectionSourceConfig> sources = jdbcTemplate.query("""
        select id, name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
               circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json,
               next_run_time, last_run_time, last_status, last_error, create_time, update_time
          from admin_collection_sources
         where status = 'ENABLED'
           and next_run_time is not null
           and next_run_time <= ?
         order by next_run_time asc
        """, sourceMapper(), Timestamp.valueOf(now));
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
    // 防止同一采集源被定时任务重复并发执行。
    Integer count = jdbcTemplate.queryForObject("""
        select count(*)
          from admin_collection_job_runs
         where source_config_id = ?
           and status = 'RUNNING'
        """, Integer.class, sourceConfigId);
    return count != null && count > 0;
  }

  private CollectionJobRun executeSource(long sourceConfigId, String triggerType, String actor, boolean scheduled) {
    // 采集执行主流程：创建 job/run、调用 Collector、关键词过滤、入库、生成审核工单并记录结果。
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
    List<CollectionKeyword> keywords = listKeywords(sourceConfigId).stream()
        .filter(keyword -> "ACTIVE".equals(keyword.status()))
        .toList();

    int attempt = 0;
    int maxAttempts = Math.max(1, source.maxRetries() == null ? 1 : source.maxRetries() + 1);
    Exception failure = null;
    while (attempt < maxAttempts) {
      attempt += 1;
      try {
        List<Map<String, Object>> records = collectorClient.collectAndGovern(source.sourceType(), payload);
        List<Map<String, Object>> filtered = applyKeywords(records, keywords);
        int persisted = persistRawRecords(filtered, source, jobId, keywords);

        // 采集到的治理记录先进入情报池，再生成审核工单，避免未经审核直接影响 RAG。
        int intelCreated = 0;
        int ticketsCreated = 0;
        if (!filtered.isEmpty()) {
          List<IntelligenceItem> intelItems = intelligenceStore.bulkCreateFromRecords(filtered);
          intelCreated = intelItems.size();
          ticketsCreated = reviewStore.createTickets(intelItems);
          if (intelCreated > 0) {
            log.info("Collection auto-created {} intelligence items and {} review tickets (source={}, job={})",
                intelCreated, ticketsCreated, source.name(), jobId);
          }
        }

        markRunSuccess(source, runId, jobId, records.size(), records.size() - filtered.size(), persisted, attempt - 1);
        writeAudit(actor, "ADMIN_COLLECTION_RUN_SOURCE", "collection_source", String.valueOf(sourceConfigId), "SUCCESS", source.regionId(), source.industryId());
        return findRun(runId);
      } catch (Exception exception) {
        // 捕获后继续重试；最终失败统一进入 markRunFailure。
        failure = exception;
      }
    }

    markRunFailure(source, runId, jobId, attempt - 1, failure == null ? "unknown collection failure" : failure.getMessage(), payload);
    writeAudit(actor, "ADMIN_COLLECTION_RUN_SOURCE", "collection_source", String.valueOf(sourceConfigId), "FAILED", source.regionId(), source.industryId());
    return findRun(runId);
  }

  private long createSource(CollectionSourceUpsertRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_collection_sources
            (name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
             circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json,
             next_run_time, last_status)
          values (?, ?, ?, ?, ?, ?, ?, 'CLOSED', 0, ?, ?, ?, ?, ?, ?, 'IDLE')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, blankToDefault(request.name(), "Untitled source"));
      ps.setString(2, normalizeSourceType(request.sourceType()));
      ps.setString(3, blankToDefault(request.status(), "ENABLED"));
      ps.setInt(4, positiveOrDefault(request.intervalMinutes(), 30));
      ps.setInt(5, positiveOrDefault(request.maxRetries(), 1));
      ps.setInt(6, positiveOrDefault(request.failureThreshold(), 3));
      ps.setInt(7, positiveOrDefault(request.cooldownMinutes(), 30));
      ps.setString(8, blankToDefault(request.regionId(), "cn-default"));
      ps.setString(9, blankToDefault(request.industryId(), "general"));
      ps.setString(10, blankToDefault(request.linkId(), "collection"));
      ps.setString(11, blankToDefault(request.sourceId(), "admin-collector"));
      ps.setString(12, blankJson(request.payloadJson()));
      ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
      return ps;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  private long updateSource(CollectionSourceUpsertRequest request) {
    long sourceConfigId = request.sourceConfigId() == null ? 0L : request.sourceConfigId();
    CollectionSourceConfig existing = findSource(sourceConfigId);
    jdbcTemplate.update("""
        update admin_collection_sources
           set name = ?, source_type = ?, status = ?, interval_minutes = ?, max_retries = ?, failure_threshold = ?,
               cooldown_minutes = ?, region_id = ?, industry_id = ?, link_id = ?, source_id = ?, payload_json = ?,
               next_run_time = ?, update_time = current_timestamp
         where id = ?
        """,
        blankToDefault(request.name(), existing.name()),
        normalizeSourceType(blankToDefault(request.sourceType(), existing.sourceType())),
        blankToDefault(request.status(), existing.status()),
        positiveOrDefault(request.intervalMinutes(), existing.intervalMinutes()),
        positiveOrDefault(request.maxRetries(), existing.maxRetries()),
        positiveOrDefault(request.failureThreshold(), existing.failureThreshold()),
        positiveOrDefault(request.cooldownMinutes(), existing.cooldownMinutes()),
        blankToDefault(request.regionId(), existing.regionId()),
        blankToDefault(request.industryId(), existing.industryId()),
        blankToDefault(request.linkId(), existing.linkId()),
        blankToDefault(request.sourceId(), existing.sourceId()),
        request.payloadJson() == null ? existing.payloadJson() : blankJson(request.payloadJson()),
        Timestamp.valueOf(nextRunTimeForStatus(blankToDefault(request.status(), existing.status()), positiveOrDefault(request.intervalMinutes(), existing.intervalMinutes()))),
        sourceConfigId);
    return sourceConfigId;
  }

  private long createKeyword(CollectionKeywordUpsertRequest request) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_collection_keywords
            (source_config_id, keyword, match_mode, status, notes)
          values (?, ?, ?, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, request.sourceConfigId());
      ps.setString(2, blankToDefault(request.keyword(), "keyword"));
      ps.setString(3, normalizeMatchMode(request.matchMode()));
      ps.setString(4, blankToDefault(request.status(), "ACTIVE"));
      ps.setString(5, request.notes());
      return ps;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  private long updateKeyword(CollectionKeywordUpsertRequest request) {
    long keywordId = request.keywordId() == null ? 0L : request.keywordId();
    CollectionKeyword existing = findKeyword(keywordId);
    jdbcTemplate.update("""
        update admin_collection_keywords
           set keyword = ?, match_mode = ?, status = ?, notes = ?, update_time = current_timestamp
         where id = ?
        """,
        blankToDefault(request.keyword(), existing.keyword()),
        normalizeMatchMode(blankToDefault(request.matchMode(), existing.matchMode())),
        blankToDefault(request.status(), existing.status()),
        request.notes() == null ? existing.notes() : request.notes(),
        keywordId);
    return keywordId;
  }

  private long createCollectionJob(CollectionSourceConfig source) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into collection_jobs
            (source_type, status, queue_depth, retry_count, circuit_state, source_id, weight, region_id, industry_id)
          values (?, 'RUNNING', 0, 0, ?, ?, 1.0000, ?, ?)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, source.sourceType());
      ps.setString(2, source.circuitState());
      ps.setString(3, source.sourceId());
      ps.setString(4, source.regionId());
      ps.setString(5, source.industryId());
      return ps;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  private long createCollectionRun(CollectionSourceConfig source, String triggerType, long jobId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_collection_job_runs
            (job_id, source_config_id, source_type, trigger_type, status, records_collected, records_filtered, records_persisted, start_time)
          values (?, ?, ?, ?, 'RUNNING', 0, 0, 0, current_timestamp)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, jobId);
      ps.setLong(2, source.sourceConfigId());
      ps.setString(3, source.sourceType());
      ps.setString(4, triggerType);
      return ps;
    }, keyHolder);
    return generatedId(keyHolder);
  }

  private void markRunSuccess(CollectionSourceConfig source, long runId, long jobId, int recordsCollected, int recordsFiltered, int recordsPersisted, int retryCount) {
    // 成功后关闭熔断、清零失败计数，并按 interval 计算下一次运行时间。
    LocalDateTime now = LocalDateTime.now();
    jdbcTemplate.update("""
        update collection_jobs
           set status = 'SUCCESS', queue_depth = ?, retry_count = ?, circuit_state = 'CLOSED', update_time = current_timestamp
         where id = ?
        """, recordsCollected, retryCount, jobId);
    jdbcTemplate.update("""
        update admin_collection_job_runs
           set status = 'SUCCESS', records_collected = ?, records_filtered = ?, records_persisted = ?, finish_time = current_timestamp
         where id = ?
        """, recordsCollected, recordsFiltered, recordsPersisted, runId);
    jdbcTemplate.update("""
        update admin_collection_sources
           set circuit_state = 'CLOSED', failure_count = 0, last_run_time = ?, next_run_time = ?,
               last_status = 'SUCCESS', last_error = null, update_time = current_timestamp
         where id = ?
        """, Timestamp.valueOf(now), Timestamp.valueOf(now.plusMinutes(Math.max(1, source.intervalMinutes()))), source.sourceConfigId());
  }

  private void markRunFailure(CollectionSourceConfig source, long runId, long jobId, int retryCount, String errorMessage, Map<String, Object> payload) {
    // 失败次数达到阈值后打开熔断并写入死信表；否则按正常间隔等待下次调度。
    int failureCount = (source.failureCount() == null ? 0 : source.failureCount()) + 1;
    boolean openCircuit = failureCount >= Math.max(1, source.failureThreshold());
    String jobStatus = openCircuit ? "DEAD_LETTER" : "FAILED";
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime nextRun = openCircuit ? now.plusMinutes(Math.max(1, source.cooldownMinutes())) : now.plusMinutes(Math.max(1, source.intervalMinutes()));
    jdbcTemplate.update("""
        update collection_jobs
           set status = ?, retry_count = ?, circuit_state = ?, update_time = current_timestamp
         where id = ?
        """, jobStatus, retryCount, openCircuit ? "OPEN" : "CLOSED", jobId);
    jdbcTemplate.update("""
        update admin_collection_job_runs
           set status = ?, error_message = ?, finish_time = current_timestamp
         where id = ?
        """, jobStatus, errorMessage, runId);
    jdbcTemplate.update("""
        update admin_collection_sources
           set circuit_state = ?, failure_count = ?, last_run_time = ?, next_run_time = ?,
               last_status = ?, last_error = ?, update_time = current_timestamp
         where id = ?
        """, openCircuit ? "OPEN" : "CLOSED", failureCount, Timestamp.valueOf(now), Timestamp.valueOf(nextRun), jobStatus, errorMessage, source.sourceConfigId());
    jdbcTemplate.update("""
        insert into dead_letter_records (job_id, reason, error_message, payload_json, source_id, weight, region_id, industry_id)
        values (?, ?, ?, ?, ?, 1.0000, ?, ?)
        """, jobId, openCircuit ? "CIRCUIT_OPEN" : "COLLECTOR_FAILURE", errorMessage, json(payload), source.sourceId(), source.regionId(), source.industryId());
  }

  private int persistRawRecords(List<Map<String, Object>> records, CollectionSourceConfig source, long jobId, List<CollectionKeyword> keywords) {
    // raw_records 保存采集治理后的原始证据，metadata 记录来源配置和命中的关键词。
    int persisted = 0;
    for (Map<String, Object> record : records) {
      jdbcTemplate.update("""
          insert into raw_records
            (source_type, title, content, url, confidence, link_id, region_id, industry_id, source_id, weight, metadata_json)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          blankToDefault(stringValue(record.get("source_type")), source.sourceType()),
          blankToDefault(stringValue(record.get("title")), source.name()),
          blankToDefault(stringValue(record.get("content")), ""),
          stringValue(record.get("url")),
          decimalValue(record.get("confidence"), BigDecimal.valueOf(0.6)),
          blankToDefault(stringValue(record.get("link_id")), source.linkId()),
          blankToDefault(stringValue(record.get("region_id")), source.regionId()),
          blankToDefault(stringValue(record.get("industry_id")), source.industryId()),
          blankToDefault(stringValue(record.get("source_id")), source.sourceId()),
          decimalValue(record.get("weight"), BigDecimal.valueOf(0.6)),
          json(Map.of(
              "sourceConfigId", source.sourceConfigId(),
              "collectionJobId", jobId,
              "appliedKeywords", keywords.stream().map(CollectionKeyword::keyword).toList())));
      persisted += 1;
    }
    return persisted;
  }

  private List<Map<String, Object>> applyKeywords(List<Map<String, Object>> records, List<CollectionKeyword> keywords) {
    // INCLUDE 为空表示不过滤包含词；EXCLUDE 命中则始终排除。
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
    return jdbcTemplate.query("""
        select r.id, r.job_id, r.source_config_id, s.name as source_name, r.source_type, r.trigger_type, r.status,
               j.queue_depth, j.retry_count, j.circuit_state, r.records_collected, r.records_filtered,
               r.records_persisted, r.error_message, r.start_time, r.finish_time
          from admin_collection_job_runs r
          left join collection_jobs j on j.id = r.job_id
          left join admin_collection_sources s on s.id = r.source_config_id
         where r.source_config_id = ?
         order by r.id desc
         limit ?
        """, runMapper(), sourceConfigId, limit);
  }

  private CollectionSourceConfig findSource(long sourceConfigId) {
    return jdbcTemplate.query("""
        select id, name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
               circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json,
               next_run_time, last_run_time, last_status, last_error, create_time, update_time
          from admin_collection_sources
         where id = ?
        """, sourceMapper(), sourceConfigId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("collection source not found"));
  }

  private CollectionKeyword findKeyword(long keywordId) {
    return jdbcTemplate.query("""
        select id, source_config_id, keyword, match_mode, status, notes, create_time, update_time
          from admin_collection_keywords
         where id = ?
        """, keywordMapper(), keywordId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("collection keyword not found"));
  }

  private CollectionJobRun findRun(long runId) {
    return jdbcTemplate.query("""
        select r.id, r.job_id, r.source_config_id, s.name as source_name, r.source_type, r.trigger_type, r.status,
               j.queue_depth, j.retry_count, j.circuit_state, r.records_collected, r.records_filtered,
               r.records_persisted, r.error_message, r.start_time, r.finish_time
          from admin_collection_job_runs r
          left join collection_jobs j on j.id = r.job_id
          left join admin_collection_sources s on s.id = r.source_config_id
         where r.id = ?
        """, runMapper(), runId).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("collection run not found"));
  }

  private Map<String, Object> payloadForSource(CollectionSourceConfig source) {
    try {
      Map<String, Object> payload = StringUtils.hasText(source.payloadJson())
          ? objectMapper.readValue(source.payloadJson(), MAP_TYPE)
          : new LinkedHashMap<>();
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
    return value == null ? null : String.valueOf(value);
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

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }

  private void writeAudit(String actor, String action, String targetType, String targetId, String result, String regionId, String industryId) {
    jdbcTemplate.update("""
        insert into audit_logs (actor, action, target_type, target_id, result, region_id, industry_id)
        values (?, ?, ?, ?, ?, ?, ?)
        """, actor, action, targetType, targetId, result, regionId, industryId);
  }

  private LocalDateTime timestamp(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }

  private RowMapper<CollectionSourceConfig> sourceMapper() {
    return (rs, rowNum) -> new CollectionSourceConfig(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("source_type"),
        rs.getString("status"),
        rs.getInt("interval_minutes"),
        rs.getInt("max_retries"),
        rs.getInt("failure_threshold"),
        rs.getInt("cooldown_minutes"),
        rs.getString("circuit_state"),
        rs.getInt("failure_count"),
        rs.getString("region_id"),
        rs.getString("industry_id"),
        rs.getString("link_id"),
        rs.getString("source_id"),
        rs.getString("payload_json"),
        timestamp(rs.getTimestamp("next_run_time")),
        timestamp(rs.getTimestamp("last_run_time")),
        rs.getString("last_status"),
        rs.getString("last_error"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<CollectionKeyword> keywordMapper() {
    return (rs, rowNum) -> new CollectionKeyword(
        rs.getLong("id"),
        rs.getLong("source_config_id"),
        rs.getString("keyword"),
        rs.getString("match_mode"),
        rs.getString("status"),
        rs.getString("notes"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<CollectionJobRun> runMapper() {
    return (rs, rowNum) -> new CollectionJobRun(
        rs.getLong("id"),
        rs.getLong("job_id"),
        rs.getLong("source_config_id"),
        rs.getString("source_name"),
        rs.getString("source_type"),
        rs.getString("trigger_type"),
        rs.getString("status"),
        rs.getInt("queue_depth"),
        rs.getInt("retry_count"),
        rs.getString("circuit_state"),
        rs.getInt("records_collected"),
        rs.getInt("records_filtered"),
        rs.getInt("records_persisted"),
        rs.getString("error_message"),
        timestamp(rs.getTimestamp("start_time")),
        timestamp(rs.getTimestamp("finish_time")));
  }

  private RowMapper<CollectionDeadLetter> deadLetterMapper() {
    return (rs, rowNum) -> new CollectionDeadLetter(
        rs.getLong("id"),
        (Long) rs.getObject("job_id"),
        rs.getString("source_name"),
        rs.getString("reason"),
        rs.getString("error_message"),
        rs.getString("payload_json"),
        timestamp(rs.getTimestamp("create_time")));
  }
}

