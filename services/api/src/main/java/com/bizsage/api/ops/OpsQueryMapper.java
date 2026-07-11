package com.bizsage.api.ops;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OpsQueryMapper {
  @Select("""
      select alert_level as level, component as scope, status, owner, message
        from alert_events
       order by id desc
      """)
  List<Map<String, Object>> listAlerts();

  @Select("""
      select id, reason, status, source_id as sourceId, target_type as targetType, target_id as targetId
        from review_work_orders
       order by id desc
      """)
  List<Map<String, Object>> listReviewWorkOrders();

  @Select("""
      select id, action, actor, result, target_type as targetType, target_id as targetId
        from audit_logs
       order by id desc
      """)
  List<Map<String, Object>> listAuditLogs();

  @Select("""
      select count(*) from audit_logs
       where result = #{result} and create_time >= #{since} and create_time < #{until}
      """)
  Integer countAuditLogsByResult(
      @Param("result") String result,
      @Param("since") Instant since,
      @Param("until") Instant until);

  @Select("""
      select count(*) from audit_logs
       where result = 'ERROR'
         and create_time >= timestampadd(minute, -5, current_timestamp)
      """)
  Integer countRecentApiErrors();

  @Select("""
      select count(*) from audit_logs
       where create_time >= timestampadd(minute, -5, current_timestamp)
      """)
  Integer countRecentApiCalls();

  @Select("""
      select count(*) from information_schema.processlist where db = database()
      """)
  Integer countActiveDbConnections();

  @Select("""
      select count(*) from knowledge_items
       where update_time < timestampadd(day, -#{days}, current_timestamp)
      """)
  Integer countStaleKnowledgeItems(@Param("days") int days);

  @Select("""
      select count(*) from alert_events
       where component = #{component} and status not in ('CLOSED')
       limit 1
      """)
  Integer countOpenAlertsForComponent(@Param("component") String component);

  @Insert("""
      insert into alert_events (alert_level, component, message, status, region_id, industry_id)
      values (#{level}, #{component}, #{message}, 'OPEN', 'global', 'global')
      """)
  int insertAlert(@Param("level") String level, @Param("component") String component, @Param("message") String message);
}
