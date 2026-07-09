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
public interface AdminKnowledgeMapper {
  @Select("""
      select n.id as nodeId, n.title, n.slug, n.industry_id as industryId, n.region_id as regionId,
             n.link_id as linkId, n.status, n.published_version_id as publishedVersionId,
             (select count(*) from admin_knowledge_versions v where v.node_id = n.id) as versionCount,
             n.update_time as updateTime
        from admin_knowledge_nodes n
       order by n.industry_id, n.region_id, n.link_id, n.title
      """)
  List<Map<String, Object>> listNodes();

  @Select("""
      select n.id as nodeId, n.title, n.slug, n.industry_id as industryId, n.region_id as regionId,
             n.link_id as linkId, n.status, n.published_version_id as publishedVersionId,
             (select count(*) from admin_knowledge_versions v where v.node_id = n.id) as versionCount,
             n.update_time as updateTime
        from admin_knowledge_nodes n
       where n.id = #{nodeId}
      """)
  Map<String, Object> findNode(@Param("nodeId") long nodeId);

  @Select("""
      select id as versionId, node_id as nodeId, version_number as versionNumber, title, summary, content,
             source_url as sourceUrl, review_status as reviewStatus, author, reviewer, review_notes as reviewNotes,
             change_notes as changeNotes, confidence, created_by_action as createdByAction,
             create_time as createTime, update_time as updateTime
        from admin_knowledge_versions
       where node_id = #{nodeId}
       order by version_number desc
      """)
  List<Map<String, Object>> listVersions(@Param("nodeId") long nodeId);

  @Select("""
      select id as publicationId, node_id as nodeId, version_id as versionId, action, actor, notes, create_time as createTime
        from admin_knowledge_publications
       where node_id = #{nodeId}
       order by id desc
      """)
  List<Map<String, Object>> listPublications(@Param("nodeId") long nodeId);

  @Insert("""
      insert into admin_knowledge_nodes
        (title, slug, industry_id, region_id, link_id, status, source_id, weight)
      values
        (#{title}, #{slug}, #{industryId}, #{regionId}, #{linkId}, 'DRAFT', 'admin-knowledge', 1.0000)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertNode(Map<String, Object> values);

  @Select("""
      select coalesce(max(version_number), 0) + 1
        from admin_knowledge_versions
       where node_id = #{nodeId}
      """)
  Integer nextVersionNumber(@Param("nodeId") long nodeId);

  @Insert("""
      insert into admin_knowledge_versions
        (node_id, version_number, title, summary, content, source_url, review_status,
         author, confidence, change_notes, created_by_action)
      values
        (#{nodeId}, #{versionNumber}, #{title}, #{summary}, #{content}, #{sourceUrl}, 'DRAFT',
         #{author}, #{confidence}, #{changeNotes}, 'SAVE_DRAFT')
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  int insertVersion(Map<String, Object> values);

  @Update("""
      update admin_knowledge_nodes
         set title = #{title}, slug = #{slug}, industry_id = #{industryId}, region_id = #{regionId},
             link_id = #{linkId}, status = 'DRAFT', draft_version_id = #{draftVersionId},
             review_version_id = null, update_time = current_timestamp
       where id = #{nodeId}
      """)
  int updateNodeForDraft(Map<String, Object> values);

  @Update("""
      update admin_knowledge_versions
         set review_status = #{reviewStatus}, reviewer = #{reviewer}, review_notes = #{reviewNotes}, update_time = current_timestamp
       where id = #{versionId}
      """)
  int updateVersionReview(
      @Param("versionId") long versionId,
      @Param("reviewStatus") String reviewStatus,
      @Param("reviewer") String reviewer,
      @Param("reviewNotes") String reviewNotes);

  @Update("""
      update admin_knowledge_nodes
         set status = #{status}, draft_version_id = #{draftVersionId}, review_version_id = #{reviewVersionId},
             update_time = current_timestamp
       where id = #{nodeId}
      """)
  int updateNodeReviewState(
      @Param("nodeId") long nodeId,
      @Param("status") String status,
      @Param("draftVersionId") Long draftVersionId,
      @Param("reviewVersionId") Long reviewVersionId);

  @Update("""
      update admin_knowledge_nodes
         set title = #{title}, slug = #{slug}, status = 'PUBLISHED', draft_version_id = null,
             review_version_id = null, published_version_id = #{publishedVersionId}, update_time = current_timestamp
       where id = #{nodeId}
      """)
  int publishNode(
      @Param("nodeId") long nodeId,
      @Param("title") String title,
      @Param("slug") String slug,
      @Param("publishedVersionId") long publishedVersionId);

  @Insert("""
      insert into admin_knowledge_publications (node_id, version_id, action, actor, notes)
      values (#{nodeId}, #{versionId}, #{action}, #{actor}, #{notes})
      """)
  int insertPublication(
      @Param("nodeId") long nodeId,
      @Param("versionId") long versionId,
      @Param("action") String action,
      @Param("actor") String actor,
      @Param("notes") String notes);

  @Select("""
      select id as versionId, node_id as nodeId, version_number as versionNumber, title, summary, content,
             source_url as sourceUrl, review_status as reviewStatus, author, reviewer, review_notes as reviewNotes,
             change_notes as changeNotes, confidence, created_by_action as createdByAction,
             create_time as createTime, update_time as updateTime
        from admin_knowledge_versions
       where id = #{versionId}
      """)
  Map<String, Object> findVersion(@Param("versionId") long versionId);

  @Insert("""
      insert into audit_logs (actor, action, target_type, target_id, result, region_id, industry_id)
      values (#{actor}, #{action}, #{targetType}, #{targetId}, #{result}, #{regionId}, #{industryId})
      """)
  int insertAuditLog(Map<String, Object> values);
}
