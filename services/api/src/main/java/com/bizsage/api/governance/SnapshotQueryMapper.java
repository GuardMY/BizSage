package com.bizsage.api.governance;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SnapshotQueryMapper {
  @Select("""
      select distinct concat(region_id, '|', industry_id) from intelligence where status = 'APPROVED'
      """)
  List<String> distinctScopes();

  @Select("""
      select id, title, content, status, confidence, link_id, region_id, industry_id, source_id, weight, url
        from intelligence
       where status = 'APPROVED' and region_id = #{regionId} and industry_id = #{industryId}
      """)
  List<Map<String, Object>> listApprovedIntelligenceRecords(
      @Param("regionId") String regionId,
      @Param("industryId") String industryId);

  @Select("""
      select link_id, count(*) as record_count, avg(confidence) as avg_confidence,
             avg(weight) as avg_weight, max(create_time) as latest
        from intelligence
       where status = 'APPROVED' and region_id = #{regionId} and industry_id = #{industryId}
       group by link_id
      """)
  List<Map<String, Object>> listWeeklySnapshotAggregates(
      @Param("regionId") String regionId,
      @Param("industryId") String industryId);

  @Select("""
      select link_id, count(*) as record_count, avg(confidence) as avg_confidence,
             avg(weight) as avg_weight,
             count(case when create_time >= date_sub(current_timestamp, interval 30 day) then 1 end) as new_this_month,
             max(create_time) as latest
        from intelligence
       where status = 'APPROVED' and region_id = #{regionId} and industry_id = #{industryId}
       group by link_id
      """)
  List<Map<String, Object>> listMonthlySnapshotAggregates(
      @Param("regionId") String regionId,
      @Param("industryId") String industryId);
}
