package com.bizsage.api.admin;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminStoreMapper {
  @Select("select count(*) from admin_intelligence_reviews where review_status = 'PENDING'")
  Integer countPendingReviews();

  @Select("select count(*) from admin_intelligence_reviews where review_status = 'COMPLETED'")
  Integer countCompletedReviews();

  @Select("select count(*) from admin_tickets where status not in ('CLOSED','ARCHIVED')")
  Integer countOpenTickets();

  @Select("select count(*) from admin_tickets where severity = 'P0' and status not in ('CLOSED','ARCHIVED')")
  Integer countP0Tickets();

  @Select("select count(*) from alert_events where status <> 'CLOSED'")
  Integer countOpenAlerts();

  @Select("select count(*) from alert_events where alert_level = 'P0' and status <> 'CLOSED'")
  Integer countOpenP0Alerts();

  @Select("select count(*) from audit_logs")
  Integer countAuditLogs();

  @Select("select count(*) from admin_human_intelligence")
  Integer countHumanIntelligence();

  @Select("select count(*) from admin_human_intelligence where status = 'PENDING_REVIEW'")
  Integer countPendingHumanIntelligence();

  @Select("select count(*) from admin_human_intelligence where status = 'APPROVED'")
  Integer countApprovedHumanIntelligence();

  @Select("select count(*) from admin_collection_sources")
  Integer countCollectionSources();

  @Select("select count(*) from admin_collection_sources where status = 'ENABLED'")
  Integer countEnabledCollectionSources();

  @Select("select count(*) from admin_collection_sources where circuit_state = 'OPEN'")
  Integer countOpenCollectionCircuits();

  @Select("select count(*) from admin_collection_job_runs")
  Integer countCollectionRuns();

  @Select("select count(*) from admin_collection_job_runs where status = 'SUCCESS'")
  Integer countSuccessfulCollectionRuns();

  @Select("select count(*) from admin_knowledge_nodes")
  Integer countKnowledgeNodes();

  @Select("select count(*) from dead_letter_records")
  Integer countDeadLetters();

  @Select("""
      select count(*) from admin_collection_job_runs
       where start_time >= timestampadd(hour, -24, current_timestamp)
      """)
  Integer countCollectionRuns24h();

  @Select("""
      select count(*) from admin_collection_job_runs
       where status = 'SUCCESS'
         and start_time >= timestampadd(hour, -24, current_timestamp)
      """)
  Integer countSuccessfulCollectionRuns24h();

  @Select("""
      select coalesce(sum(records_collected), 0) from admin_collection_job_runs
       where start_time >= timestampadd(hour, -24, current_timestamp)
      """)
  Integer sumCollectedRecords24h();

  @Select("""
      select r.status as status, r.records_collected as recordsCollected, r.start_time as startTime, s.name as sourceName
        from admin_collection_job_runs r
        left join admin_collection_sources s on s.id = r.source_config_id
       order by r.id desc
       limit 10
      """)
  List<Map<String, Object>> listRecentCollectionRuns();

  @Select("""
      <script>
      select id, alert_level as level, component, message, status, owner,
             region_id as regionId, industry_id as industryId, create_time as createTime, update_time as updateTime
        from alert_events
      <if test="status != null and status != ''">
       where status = #{status}
      </if>
       order by case alert_level when 'P0' then 0 when 'P1' then 1 else 2 end, id desc
      </script>
      """)
  List<Map<String, Object>> listAlerts(@Param("status") String status);

  @Select("""
      select id, alert_level as level, component, message, status, owner,
             region_id as regionId, industry_id as industryId, create_time as createTime, update_time as updateTime
        from alert_events
       where id = #{id}
      """)
  Map<String, Object> findAlert(@Param("id") long id);

  @Update("""
      update alert_events
         set status = #{status}, owner = #{owner}, update_time = current_timestamp
       where id = #{id}
      """)
  int updateAlert(@Param("id") long id, @Param("status") String status, @Param("owner") String owner);

  @Select("""
      <script>
      select id, actor, action, target_type as targetType, target_id as targetId,
             result, region_id as regionId, industry_id as industryId, create_time as createTime
        from audit_logs
       where (#{like} = '%%' or actor like #{like} or action like #{like} or target_type like #{like} or target_id like #{like})
       order by id desc
      </script>
      """)
  List<Map<String, Object>> listAuditLogs(@Param("like") String like);

  @Select("""
      <script>
      select r.id, r.intelligence_id as intelligenceId, i.title, i.content, i.url, i.status,
             r.review_status as reviewStatus, r.verdict, r.reviewer, r.reason,
             i.confidence, r.region_id as regionId, r.industry_id as industryId, i.source_id as sourceId,
             r.create_time as createTime, r.update_time as updateTime
        from admin_intelligence_reviews r
        left join intelligence i on i.id = r.intelligence_id
      <if test="status != null and status != ''">
       where r.review_status = #{status}
      </if>
       order by r.id desc
      </script>
      """)
  List<Map<String, Object>> listReviews(@Param("status") String status);

  @Select("""
      select r.id, r.intelligence_id as intelligenceId, i.title, i.content, i.url, i.status,
             r.review_status as reviewStatus, r.verdict, r.reviewer, r.reason,
             i.confidence, r.region_id as regionId, r.industry_id as industryId, i.source_id as sourceId,
             r.create_time as createTime, r.update_time as updateTime
        from admin_intelligence_reviews r
        left join intelligence i on i.id = r.intelligence_id
       where r.id = #{id}
      """)
  Map<String, Object> findReview(@Param("id") long id);

  @Update("""
      update admin_intelligence_reviews
         set review_status = 'COMPLETED', verdict = #{verdict}, reviewer = #{reviewer},
             reason = #{reason}, update_time = current_timestamp
       where id = #{id}
      """)
  int completeReview(
      @Param("id") long id,
      @Param("verdict") String verdict,
      @Param("reviewer") String reviewer,
      @Param("reason") String reason);

  @Insert("""
      insert into admin_tickets
        (ticket_type, severity, target_type, target_id, title, description, status, owner, next_action, region_id, industry_id)
      values
        (#{ticketType}, #{severity}, #{targetType}, #{targetId}, #{title}, #{description}, #{status}, #{owner}, #{nextAction}, #{regionId}, #{industryId})
      """)
  int insertTicket(Map<String, Object> values);

  @Select("""
      <script>
      select id, ticket_type as ticketType, severity, target_type as targetType, target_id as targetId, title,
             status, owner, next_action as nextAction, region_id as regionId, industry_id as industryId,
             create_time as createTime, update_time as updateTime
        from admin_tickets
      <if test="status != null and status != ''">
       where status = #{status}
      </if>
       order by case severity when 'P0' then 0 when 'P1' then 1 else 2 end, id desc
      </script>
      """)
  List<Map<String, Object>> listTickets(@Param("status") String status);

  @Select("""
      select id, ticket_type as ticketType, severity, target_type as targetType, target_id as targetId, title,
             status, owner, next_action as nextAction, region_id as regionId, industry_id as industryId,
             create_time as createTime, update_time as updateTime
        from admin_tickets
       where id = #{id}
      """)
  Map<String, Object> findTicket(@Param("id") long id);

  @Update("""
      update admin_tickets
         set status = #{status}, owner = #{owner}, next_action = #{nextAction}, update_time = current_timestamp
       where id = #{id}
      """)
  int transitionTicket(
      @Param("id") long id,
      @Param("status") String status,
      @Param("owner") String owner,
      @Param("nextAction") String nextAction);

  @Select("""
      <script>
      select id, city, industry_id as industryId, link_id as linkId, content, source_type as sourceType,
             collector, event_time as eventTime, confidence, entitlement, status, reviewer,
             review_notes as reviewNotes, region_id as regionId, source_id as sourceId,
             create_time as createTime, update_time as updateTime
        from admin_human_intelligence
      <if test="status != null and status != ''">
       where status = #{status}
      </if>
       order by id desc
      </script>
      """)
  List<Map<String, Object>> listHumanIntelligence(@Param("status") String status);

  @Select("""
      select id, city, industry_id as industryId, link_id as linkId, content, source_type as sourceType,
             collector, event_time as eventTime, confidence, entitlement, status, reviewer,
             review_notes as reviewNotes, region_id as regionId, source_id as sourceId,
             create_time as createTime, update_time as updateTime
        from admin_human_intelligence
       where id = #{id}
      """)
  Map<String, Object> findHumanIntelligence(@Param("id") long id);

  @Insert("""
      insert into admin_human_intelligence
        (city, industry_id, link_id, content, source_type, collector, event_time,
         confidence, entitlement, status, region_id, source_id)
      values
        (#{city}, #{industryId}, #{linkId}, #{content}, #{sourceType}, #{collector}, #{eventTime},
         #{confidence}, #{entitlement}, 'PENDING_REVIEW', #{regionId}, #{sourceId})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertHumanIntelligence(Map<String, Object> values);

  @Update("""
      update admin_human_intelligence
         set status = #{status}, reviewer = #{reviewer}, review_notes = #{reviewNotes}, update_time = current_timestamp
       where id = #{id}
      """)
  int reviewHumanIntelligence(
      @Param("id") long id,
      @Param("status") String status,
      @Param("reviewer") String reviewer,
      @Param("reviewNotes") String reviewNotes);

  @Insert("""
      insert into intelligence
        (title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, content_hash)
      values
        (#{title}, #{content}, #{url}, 'APPROVED', #{confidence}, #{linkId}, #{regionId}, #{industryId}, #{sourceId}, #{weight}, #{contentHash})
      """)
  int insertHumanIntelligenceAsIntelligence(Map<String, Object> values);

  @Insert("""
      insert into audit_logs (actor, action, target_type, target_id, result, region_id, industry_id)
      values (#{actor}, #{action}, #{targetType}, #{targetId}, #{result}, #{regionId}, #{industryId})
      """)
  int insertAuditLog(Map<String, Object> values);

  @Select("""
      select id, rule_type as ruleType, name, description, enabled, threshold_value as thresholdValue,
             scope_json as scopeJson, risk_level as riskLevel, change_mode as changeMode,
             version, create_time as createTime, update_time as updateTime
        from admin_risk_rules
       order by rule_type, name
      """)
  List<Map<String, Object>> listRiskRules();

  @Select("""
      select id, rule_type as ruleType, name, description, enabled, threshold_value as thresholdValue,
             scope_json as scopeJson, risk_level as riskLevel, change_mode as changeMode,
             version, create_time as createTime, update_time as updateTime
        from admin_risk_rules
       where id = #{id}
      """)
  Map<String, Object> findRiskRule(@Param("id") long id);

  @Insert("""
      insert into admin_risk_rules
        (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode, version)
      values
        (#{ruleType}, #{name}, #{description}, #{enabled}, #{thresholdValue}, #{scopeJson}, #{riskLevel}, #{changeMode}, 1)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertRiskRule(Map<String, Object> values);

  @Update("""
      update admin_risk_rules
         set rule_type = #{ruleType}, name = #{name}, description = #{description}, enabled = #{enabled},
             threshold_value = #{thresholdValue}, scope_json = #{scopeJson}, risk_level = #{riskLevel},
             change_mode = #{changeMode}, version = version + 1, update_time = current_timestamp
       where id = #{id}
      """)
  int updateRiskRule(Map<String, Object> values);

  @Update("""
      update admin_risk_rules
         set enabled = #{enabled}, update_time = current_timestamp
       where id = #{id}
      """)
  int toggleRiskRule(@Param("id") long id, @Param("enabled") boolean enabled);

  @Select("select id from admin_collection_sources where id = #{id}")
  Long findCollectionSourceId(@Param("id") long id);

  @Update("""
      update admin_collection_sources
         set status = #{status}, update_time = current_timestamp
       where id = #{id}
      """)
  int updateCollectionSourceStatus(@Param("id") long id, @Param("status") String status);
}
