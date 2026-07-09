package com.bizsage.api.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminCollectionMapper {
  @Select("""
      select id as sourceConfigId, name, source_type as sourceType, status, interval_minutes as intervalMinutes,
             max_retries as maxRetries, failure_threshold as failureThreshold, cooldown_minutes as cooldownMinutes,
             circuit_state as circuitState, failure_count as failureCount, region_id as regionId, industry_id as industryId,
             link_id as linkId, source_id as sourceId, payload_json as payloadJson, next_run_time as nextRunTime,
             last_run_time as lastRunTime, last_status as lastStatus, last_error as lastError,
             create_time as createTime, update_time as updateTime
        from admin_collection_sources
       order by status desc, id desc
      """)
  List<Map<String, Object>> listSources();

  @Select("""
      select id as sourceConfigId, name, source_type as sourceType, status, interval_minutes as intervalMinutes,
             max_retries as maxRetries, failure_threshold as failureThreshold, cooldown_minutes as cooldownMinutes,
             circuit_state as circuitState, failure_count as failureCount, region_id as regionId, industry_id as industryId,
             link_id as linkId, source_id as sourceId, payload_json as payloadJson, next_run_time as nextRunTime,
             last_run_time as lastRunTime, last_status as lastStatus, last_error as lastError,
             create_time as createTime, update_time as updateTime
        from admin_collection_sources
       where id = #{sourceConfigId}
      """)
  Map<String, Object> findSource(@Param("sourceConfigId") long sourceConfigId);

  @Insert("""
      insert into admin_collection_sources
        (name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
         circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json, next_run_time, last_status)
      values
        (#{name}, #{sourceType}, #{status}, #{intervalMinutes}, #{maxRetries}, #{failureThreshold}, #{cooldownMinutes},
         'CLOSED', 0, #{regionId}, #{industryId}, #{linkId}, #{sourceId}, #{payloadJson}, #{nextRunTime}, 'IDLE')
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertSource(Map<String, Object> values);

  @Update("""
      update admin_collection_sources
         set name = #{name}, source_type = #{sourceType}, status = #{status}, interval_minutes = #{intervalMinutes},
             max_retries = #{maxRetries}, failure_threshold = #{failureThreshold}, cooldown_minutes = #{cooldownMinutes},
             region_id = #{regionId}, industry_id = #{industryId}, link_id = #{linkId}, source_id = #{sourceId},
             payload_json = #{payloadJson}, next_run_time = #{nextRunTime}, update_time = current_timestamp
       where id = #{sourceConfigId}
      """)
  int updateSource(Map<String, Object> values);

  @Select("""
      <script>
      select id as keywordId, source_config_id as sourceConfigId, keyword, match_mode as matchMode, status, notes,
             create_time as createTime, update_time as updateTime
        from admin_collection_keywords
      <if test="sourceConfigId != null">
       where source_config_id = #{sourceConfigId}
      </if>
       order by
      <choose>
        <when test="sourceConfigId == null">source_config_id asc, id desc</when>
        <otherwise>id desc</otherwise>
      </choose>
      </script>
      """)
  List<Map<String, Object>> listKeywords(@Param("sourceConfigId") Long sourceConfigId);

  @Select("""
      select id as keywordId, source_config_id as sourceConfigId, keyword, match_mode as matchMode, status, notes,
             create_time as createTime, update_time as updateTime
        from admin_collection_keywords
       where id = #{keywordId}
      """)
  Map<String, Object> findKeyword(@Param("keywordId") long keywordId);

  @Insert("""
      insert into admin_collection_keywords
        (source_config_id, keyword, match_mode, status, notes)
      values
        (#{sourceConfigId}, #{keyword}, #{matchMode}, #{status}, #{notes})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertKeyword(Map<String, Object> values);

  @Update("""
      update admin_collection_keywords
         set keyword = #{keyword}, match_mode = #{matchMode}, status = #{status}, notes = #{notes}, update_time = current_timestamp
       where id = #{keywordId}
      """)
  int updateKeyword(Map<String, Object> values);

  @Select("""
      <script>
      select r.id as runId, r.job_id as jobId, r.source_config_id as sourceConfigId, s.name as sourceName,
             r.source_type as sourceType, r.trigger_type as triggerType, r.status,
             j.queue_depth as queueDepth, j.retry_count as retryCount, j.circuit_state as circuitState,
             r.records_collected as recordsCollected, r.records_filtered as recordsFiltered,
             r.records_persisted as recordsPersisted, r.error_message as errorMessage,
             r.start_time as startTime, r.finish_time as finishTime
        from admin_collection_job_runs r
        left join collection_jobs j on j.id = r.job_id
        left join admin_collection_sources s on s.id = r.source_config_id
      <if test="status != null and status != ''">
       where r.status = #{status}
      </if>
       order by r.id desc
      </script>
      """)
  List<Map<String, Object>> listRuns(@Param("status") String status);

  @Select("""
      select d.id as deadLetterId, d.job_id as jobId, s.name as sourceName, d.reason, d.error_message as errorMessage,
             cast(d.payload_json as char) as payloadJson, d.create_time as createTime
        from dead_letter_records d
        left join admin_collection_job_runs r on r.job_id = d.job_id
        left join admin_collection_sources s on s.id = r.source_config_id
       order by d.id desc
      """)
  List<Map<String, Object>> listDeadLetters();

  @Select("""
      select id as sourceConfigId, name, source_type as sourceType, status, interval_minutes as intervalMinutes,
             max_retries as maxRetries, failure_threshold as failureThreshold, cooldown_minutes as cooldownMinutes,
             circuit_state as circuitState, failure_count as failureCount, region_id as regionId, industry_id as industryId,
             link_id as linkId, source_id as sourceId, payload_json as payloadJson, next_run_time as nextRunTime,
             last_run_time as lastRunTime, last_status as lastStatus, last_error as lastError,
             create_time as createTime, update_time as updateTime
        from admin_collection_sources
       where status = 'ENABLED'
         and next_run_time is not null
         and next_run_time <= #{now}
       order by next_run_time asc
      """)
  List<Map<String, Object>> listDueSources(@Param("now") LocalDateTime now);

  @Select("""
      select count(*)
        from admin_collection_job_runs
       where source_config_id = #{sourceConfigId}
         and status = 'RUNNING'
      """)
  Integer countRunningRuns(@Param("sourceConfigId") long sourceConfigId);

  @Insert("""
      insert into collection_jobs
        (source_type, status, queue_depth, retry_count, circuit_state, source_id, weight, region_id, industry_id)
      values
        (#{sourceType}, 'RUNNING', 0, 0, #{circuitState}, #{sourceId}, 1.0000, #{regionId}, #{industryId})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertCollectionJob(Map<String, Object> values);

  @Insert("""
      insert into admin_collection_job_runs
        (job_id, source_config_id, source_type, trigger_type, status, records_collected, records_filtered, records_persisted, start_time)
      values
        (#{jobId}, #{sourceConfigId}, #{sourceType}, #{triggerType}, 'RUNNING', 0, 0, 0, current_timestamp)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertCollectionRun(Map<String, Object> values);

  @Update("""
      update collection_jobs
         set status = 'SUCCESS', queue_depth = #{queueDepth}, retry_count = #{retryCount},
             circuit_state = 'CLOSED', update_time = current_timestamp
       where id = #{jobId}
      """)
  int markCollectionJobSuccess(@Param("jobId") long jobId, @Param("queueDepth") int queueDepth, @Param("retryCount") int retryCount);

  @Update("""
      update admin_collection_job_runs
         set status = 'SUCCESS', records_collected = #{recordsCollected}, records_filtered = #{recordsFiltered},
             records_persisted = #{recordsPersisted}, finish_time = current_timestamp
       where id = #{runId}
      """)
  int markRunSuccess(
      @Param("runId") long runId,
      @Param("recordsCollected") int recordsCollected,
      @Param("recordsFiltered") int recordsFiltered,
      @Param("recordsPersisted") int recordsPersisted);

  @Update("""
      update admin_collection_sources
         set circuit_state = 'CLOSED', failure_count = 0, last_run_time = #{lastRunTime},
             next_run_time = #{nextRunTime}, last_status = 'SUCCESS', last_error = null,
             update_time = current_timestamp
       where id = #{sourceConfigId}
      """)
  int markSourceSuccess(
      @Param("sourceConfigId") long sourceConfigId,
      @Param("lastRunTime") LocalDateTime lastRunTime,
      @Param("nextRunTime") LocalDateTime nextRunTime);

  @Update("""
      update collection_jobs
         set status = #{status}, retry_count = #{retryCount}, circuit_state = #{circuitState}, update_time = current_timestamp
       where id = #{jobId}
      """)
  int markCollectionJobFailure(
      @Param("jobId") long jobId,
      @Param("status") String status,
      @Param("retryCount") int retryCount,
      @Param("circuitState") String circuitState);

  @Update("""
      update admin_collection_job_runs
         set status = #{status}, error_message = #{errorMessage}, finish_time = current_timestamp
       where id = #{runId}
      """)
  int markRunFailure(
      @Param("runId") long runId,
      @Param("status") String status,
      @Param("errorMessage") String errorMessage);

  @Update("""
      update admin_collection_sources
         set circuit_state = #{circuitState}, failure_count = #{failureCount}, last_run_time = #{lastRunTime},
             next_run_time = #{nextRunTime}, last_status = #{lastStatus}, last_error = #{lastError},
             update_time = current_timestamp
       where id = #{sourceConfigId}
      """)
  int markSourceFailure(
      @Param("sourceConfigId") long sourceConfigId,
      @Param("circuitState") String circuitState,
      @Param("failureCount") int failureCount,
      @Param("lastRunTime") LocalDateTime lastRunTime,
      @Param("nextRunTime") LocalDateTime nextRunTime,
      @Param("lastStatus") String lastStatus,
      @Param("lastError") String lastError);

  @Insert("""
      insert into dead_letter_records (job_id, reason, error_message, payload_json, source_id, weight, region_id, industry_id)
      values (#{jobId}, #{reason}, #{errorMessage}, #{payloadJson}, #{sourceId}, 1.0000, #{regionId}, #{industryId})
      """)
  int insertDeadLetter(Map<String, Object> values);

  @Insert("""
      insert into raw_records
        (source_type, title, content, url, confidence, link_id, region_id, industry_id, source_id, weight, metadata_json)
      values
        (#{sourceType}, #{title}, #{content}, #{url}, #{confidence}, #{linkId}, #{regionId}, #{industryId}, #{sourceId}, #{weight}, #{metadataJson})
      """)
  int insertRawRecord(Map<String, Object> values);

  @Select("""
      select r.id as runId, r.job_id as jobId, r.source_config_id as sourceConfigId, s.name as sourceName,
             r.source_type as sourceType, r.trigger_type as triggerType, r.status,
             j.queue_depth as queueDepth, j.retry_count as retryCount, j.circuit_state as circuitState,
             r.records_collected as recordsCollected, r.records_filtered as recordsFiltered,
             r.records_persisted as recordsPersisted, r.error_message as errorMessage,
             r.start_time as startTime, r.finish_time as finishTime
        from admin_collection_job_runs r
        left join collection_jobs j on j.id = r.job_id
        left join admin_collection_sources s on s.id = r.source_config_id
       where r.source_config_id = #{sourceConfigId}
       order by r.id desc
       limit #{limit}
      """)
  List<Map<String, Object>> recentRuns(@Param("sourceConfigId") long sourceConfigId, @Param("limit") int limit);

  @Select("""
      select r.id as runId, r.job_id as jobId, r.source_config_id as sourceConfigId, s.name as sourceName,
             r.source_type as sourceType, r.trigger_type as triggerType, r.status,
             j.queue_depth as queueDepth, j.retry_count as retryCount, j.circuit_state as circuitState,
             r.records_collected as recordsCollected, r.records_filtered as recordsFiltered,
             r.records_persisted as recordsPersisted, r.error_message as errorMessage,
             r.start_time as startTime, r.finish_time as finishTime
        from admin_collection_job_runs r
        left join collection_jobs j on j.id = r.job_id
        left join admin_collection_sources s on s.id = r.source_config_id
       where r.id = #{runId}
      """)
  Map<String, Object> findRun(@Param("runId") long runId);

  @Insert("""
      insert into audit_logs (actor, action, target_type, target_id, result, region_id, industry_id)
      values (#{actor}, #{action}, #{targetType}, #{targetId}, #{result}, #{regionId}, #{industryId})
      """)
  int insertAuditLog(Map<String, Object> values);
}
