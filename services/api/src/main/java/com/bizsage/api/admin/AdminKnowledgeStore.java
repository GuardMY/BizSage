package com.bizsage.api.admin;

import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeDraftRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeNodeDetail;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgePublication;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeRollbackRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeTreeNode;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeVersion;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeVersionDiff;
import com.bizsage.api.worker.AiWorkerClient;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminKnowledgeStore {
  private final JdbcTemplate jdbcTemplate;
  private final AiWorkerClient aiWorkerClient;

  public AdminKnowledgeStore(JdbcTemplate jdbcTemplate, AiWorkerClient aiWorkerClient) {
    this.jdbcTemplate = jdbcTemplate;
    this.aiWorkerClient = aiWorkerClient;
  }

  List<KnowledgeTreeNode> listNodes() {
    return jdbcTemplate.query("""
        select n.id, n.title, n.slug, n.industry_id, n.region_id, n.link_id, n.status,
               n.published_version_id,
               (select count(*) from admin_knowledge_versions v where v.node_id = n.id) as version_count,
               n.update_time
          from admin_knowledge_nodes n
         order by n.industry_id, n.region_id, n.link_id, n.title
        """, nodeMapper());
  }

  KnowledgeNodeDetail detail(long nodeId) {
    KnowledgeTreeNode node = jdbcTemplate.query("""
        select n.id, n.title, n.slug, n.industry_id, n.region_id, n.link_id, n.status,
               n.published_version_id,
               (select count(*) from admin_knowledge_versions v where v.node_id = n.id) as version_count,
               n.update_time
          from admin_knowledge_nodes n
         where n.id = ?
        """, nodeMapper(), nodeId).stream().findFirst()
        .orElseThrow(() -> new IllegalArgumentException("knowledge node not found"));

    List<KnowledgeVersion> versions = jdbcTemplate.query("""
        select id, node_id, version_number, title, summary, content, source_url, review_status,
               author, reviewer, review_notes, change_notes, confidence, created_by_action, create_time, update_time
          from admin_knowledge_versions
         where node_id = ?
         order by version_number desc
        """, versionMapper(), nodeId);

    List<KnowledgePublication> publications = jdbcTemplate.query("""
        select id, node_id, version_id, action, actor, notes, create_time
          from admin_knowledge_publications
         where node_id = ?
         order by id desc
        """, publicationMapper(), nodeId);

    Long draftVersionId = versions.stream()
        .filter(version -> "DRAFT".equals(version.reviewStatus()))
        .map(KnowledgeVersion::versionId)
        .findFirst()
        .orElse(null);
    Long reviewVersionId = versions.stream()
        .filter(version -> "IN_REVIEW".equals(version.reviewStatus()))
        .map(KnowledgeVersion::versionId)
        .findFirst()
        .orElse(null);

    return new KnowledgeNodeDetail(
        node.nodeId(),
        node.title(),
        node.slug(),
        node.industryId(),
        node.regionId(),
        node.linkId(),
        node.status(),
        draftVersionId,
        reviewVersionId,
        node.publishedVersionId(),
        versions,
        publications);
  }

  KnowledgeNodeDetail saveDraft(KnowledgeDraftRequest request, String actor) {
    long nodeId = request.nodeId() == null ? createNode(request, actor) : request.nodeId();
    int nextVersion = nextVersionNumber(nodeId);
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_knowledge_versions
            (node_id, version_number, title, summary, content, source_url, review_status,
             author, confidence, change_notes, created_by_action)
          values (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, 'SAVE_DRAFT')
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, nodeId);
      ps.setInt(2, nextVersion);
      ps.setString(3, blankToDefault(request.title(), "Untitled node"));
      ps.setString(4, blankToDefault(request.summary(), ""));
      ps.setString(5, blankToDefault(request.content(), ""));
      ps.setString(6, request.sourceUrl());
      ps.setString(7, actor);
      ps.setBigDecimal(8, request.confidence() == null ? BigDecimal.valueOf(0.85) : request.confidence());
      ps.setString(9, blankToDefault(request.changeNotes(), "Draft saved"));
      return ps;
    }, keyHolder);
    long versionId = generatedId(keyHolder);

    jdbcTemplate.update("""
        update admin_knowledge_nodes
           set title = ?, slug = ?, industry_id = ?, region_id = ?, link_id = ?,
               status = 'DRAFT', draft_version_id = ?, review_version_id = null, update_time = current_timestamp
         where id = ?
        """,
        blankToDefault(request.title(), "Untitled node"),
        normalizedSlug(request),
        blankToDefault(request.industryId(), "general"),
        blankToDefault(request.regionId(), "cn-default"),
        blankToDefault(request.linkId(), "general"),
        versionId,
        nodeId);

    writeAudit(
        actor,
        "ADMIN_KNOWLEDGE_SAVE_DRAFT",
        "knowledge_node",
        String.valueOf(nodeId),
        "SUCCESS",
        blankToDefault(request.regionId(), "cn-default"),
        blankToDefault(request.industryId(), "general"));
    return detail(nodeId);
  }

  KnowledgeNodeDetail submitReview(long nodeId, long versionId, String notes, String actor) {
    KnowledgeVersion version = requireVersion(nodeId, versionId);
    if (!"DRAFT".equals(version.reviewStatus())) {
      throw new IllegalArgumentException("only draft versions can enter review");
    }
    jdbcTemplate.update("""
        update admin_knowledge_versions
           set review_status = 'IN_REVIEW', review_notes = ?, update_time = current_timestamp
         where id = ?
        """, blankToDefault(notes, "Submitted for review"), versionId);
    jdbcTemplate.update("""
        update admin_knowledge_nodes
           set status = 'IN_REVIEW', draft_version_id = null, review_version_id = ?, update_time = current_timestamp
         where id = ?
        """, versionId, nodeId);
    KnowledgeNodeDetail detail = detail(nodeId);
    writeAudit(actor, "ADMIN_KNOWLEDGE_SUBMIT_REVIEW", "knowledge_version", String.valueOf(versionId), "SUCCESS", detail.regionId(), detail.industryId());
    return detail;
  }

  KnowledgeNodeDetail approveReview(long nodeId, long versionId, String notes, String actor) {
    KnowledgeVersion version = requireVersion(nodeId, versionId);
    if (!"IN_REVIEW".equals(version.reviewStatus())) {
      throw new IllegalArgumentException("only in-review versions can be approved");
    }
    if (actor.equalsIgnoreCase(version.author())) {
      throw new IllegalArgumentException("reviewer must be different from author");
    }
    jdbcTemplate.update("""
        update admin_knowledge_versions
           set review_status = 'APPROVED', reviewer = ?, review_notes = ?, update_time = current_timestamp
         where id = ?
        """, actor, blankToDefault(notes, "Approved"), versionId);
    jdbcTemplate.update("""
        update admin_knowledge_nodes
           set status = 'APPROVED', draft_version_id = null, review_version_id = ?, update_time = current_timestamp
         where id = ?
        """, versionId, nodeId);
    KnowledgeNodeDetail detail = detail(nodeId);
    writeAudit(actor, "ADMIN_KNOWLEDGE_APPROVE_REVIEW", "knowledge_version", String.valueOf(versionId), "SUCCESS", detail.regionId(), detail.industryId());
    return detail;
  }

  KnowledgeNodeDetail publish(long nodeId, long versionId, String notes, String actor) {
    KnowledgeVersion version = requireVersion(nodeId, versionId);
    if (!"APPROVED".equals(version.reviewStatus())) {
      throw new IllegalArgumentException("only approved versions can be published");
    }
    KnowledgeNodeDetail current = detail(nodeId);
    jdbcTemplate.update("""
        update admin_knowledge_nodes
           set title = ?, slug = ?, status = 'PUBLISHED', draft_version_id = null, review_version_id = null,
               published_version_id = ?, update_time = current_timestamp
         where id = ?
        """, version.title(), normalizedSlug(version.title(), current.slug()), versionId, nodeId);

    jdbcTemplate.update("""
        insert into admin_knowledge_publications (node_id, version_id, action, actor, notes)
        values (?, ?, 'PUBLISH', ?, ?)
        """, nodeId, versionId, actor, blankToDefault(notes, "Published"));

    syncPublishedVersion(current, version);
    syncToQdrant(nodeId, version, current);
    KnowledgeNodeDetail detail = detail(nodeId);
    writeAudit(actor, "ADMIN_KNOWLEDGE_PUBLISH", "knowledge_version", String.valueOf(versionId), "SUCCESS", detail.regionId(), detail.industryId());
    return detail;
  }

  KnowledgeNodeDetail rollback(long nodeId, KnowledgeRollbackRequest request, String actor) {
    KnowledgeVersion version = requireVersion(nodeId, request.targetVersionId());
    if (!"APPROVED".equals(version.reviewStatus())) {
      throw new IllegalArgumentException("only approved versions can be rollback targets");
    }
    KnowledgeNodeDetail current = detail(nodeId);
    if (current.publishedVersionId() == null) {
      throw new IllegalArgumentException("rollback requires an already published node");
    }
    jdbcTemplate.update("""
        update admin_knowledge_nodes
           set title = ?, slug = ?, status = 'PUBLISHED', draft_version_id = null, review_version_id = null,
               published_version_id = ?, update_time = current_timestamp
         where id = ?
        """, version.title(), normalizedSlug(version.title(), current.slug()), version.versionId(), nodeId);
    jdbcTemplate.update("""
        insert into admin_knowledge_publications (node_id, version_id, action, actor, notes)
        values (?, ?, 'ROLLBACK', ?, ?)
        """, nodeId, version.versionId(), actor, blankToDefault(request.notes(), "Rolled back"));
    syncPublishedVersion(current, version);
    syncToQdrant(nodeId, version, current);
    KnowledgeNodeDetail detail = detail(nodeId);
    writeAudit(actor, "ADMIN_KNOWLEDGE_ROLLBACK", "knowledge_version", String.valueOf(version.versionId()), "SUCCESS", detail.regionId(), detail.industryId());
    return detail;
  }

  KnowledgeVersionDiff diff(long leftVersionId, long rightVersionId) {
    KnowledgeVersion left = findVersionById(leftVersionId);
    KnowledgeVersion right = findVersionById(rightVersionId);
    return new KnowledgeVersionDiff(
        left.versionId(),
        right.versionId(),
        left.title(),
        right.title(),
        left.summary(),
        right.summary(),
        left.content(),
        right.content(),
        left.sourceUrl(),
        right.sourceUrl(),
        left.confidence(),
        right.confidence(),
        left.reviewStatus(),
        right.reviewStatus());
  }

  private long createNode(KnowledgeDraftRequest request, String actor) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(connection -> {
      PreparedStatement ps = connection.prepareStatement("""
          insert into admin_knowledge_nodes
            (title, slug, industry_id, region_id, link_id, status, source_id, weight)
          values (?, ?, ?, ?, ?, 'DRAFT', 'admin-knowledge', 1.0000)
          """, Statement.RETURN_GENERATED_KEYS);
      ps.setString(1, blankToDefault(request.title(), "Untitled node"));
      ps.setString(2, normalizedSlug(request));
      ps.setString(3, blankToDefault(request.industryId(), "general"));
      ps.setString(4, blankToDefault(request.regionId(), "cn-default"));
      ps.setString(5, blankToDefault(request.linkId(), "general"));
      return ps;
    }, keyHolder);
    long nodeId = generatedId(keyHolder);
    writeAudit(
        actor,
        "ADMIN_KNOWLEDGE_CREATE_NODE",
        "knowledge_node",
        String.valueOf(nodeId),
        "SUCCESS",
        blankToDefault(request.regionId(), "cn-default"),
        blankToDefault(request.industryId(), "general"));
    return nodeId;
  }

  private int nextVersionNumber(long nodeId) {
    Integer version = jdbcTemplate.queryForObject("""
        select coalesce(max(version_number), 0) + 1
          from admin_knowledge_versions
         where node_id = ?
        """, Integer.class, nodeId);
    return version == null ? 1 : version;
  }

  private KnowledgeVersion requireVersion(long nodeId, long versionId) {
    KnowledgeVersion version = findVersionById(versionId);
    if (version.nodeId() != nodeId) {
      throw new IllegalArgumentException("knowledge version does not belong to node");
    }
    return version;
  }

  private KnowledgeVersion findVersionById(long versionId) {
    return jdbcTemplate.query("""
        select id, node_id, version_number, title, summary, content, source_url, review_status,
               author, reviewer, review_notes, change_notes, confidence, created_by_action, create_time, update_time
          from admin_knowledge_versions
         where id = ?
        """, versionMapper(), versionId).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("knowledge version not found"));
  }

  private void syncPublishedVersion(KnowledgeNodeDetail node, KnowledgeVersion version) {
    Long existingId = jdbcTemplate.query("""
        select id
          from knowledge_items
         where source_id = ?
        """, (rs, rowNum) -> rs.getLong("id"), "admin-node-" + node.nodeId()).stream().findFirst().orElse(null);

    if (existingId == null) {
      jdbcTemplate.update("""
          insert into knowledge_items
            (title, content, source_url, confidence, link_id, region_id, industry_id, source_id, weight)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          version.title(),
          version.content(),
          version.sourceUrl(),
          version.confidence(),
          node.linkId(),
          node.regionId(),
          node.industryId(),
          "admin-node-" + node.nodeId(),
          version.confidence());
    } else {
      jdbcTemplate.update("""
          update knowledge_items
             set title = ?, content = ?, source_url = ?, confidence = ?, link_id = ?, region_id = ?, industry_id = ?,
                 weight = ?, update_time = current_timestamp
           where id = ?
          """,
          version.title(),
          version.content(),
          version.sourceUrl(),
          version.confidence(),
          node.linkId(),
          node.regionId(),
          node.industryId(),
          version.confidence(),
          existingId);
    }
  }

  /**
   * Sync a published/rollback knowledge version to the AI worker's Qdrant
   * vector store so it is available for RAG search during diagnosis.
   *
   * <p>Failures are logged but do not block the publish flow — the MySQL
   * knowledge_items table remains the authoritative source.
   */
  private void syncToQdrant(long nodeId, KnowledgeVersion version, KnowledgeNodeDetail node) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", "admin-node-" + nodeId);
    item.put("title", version.title());
    item.put("content", version.content());
    item.put("source_url", version.sourceUrl() != null ? version.sourceUrl() : "");
    item.put("source_id", "admin-knowledge");
    item.put("weight", version.confidence() != null ? version.confidence().doubleValue() : 0.85);
    item.put("confidence", version.confidence() != null ? version.confidence().doubleValue() : 0.85);
    item.put("industry_id", node.industryId() != null ? node.industryId() : "general");
    item.put("region_id", node.regionId() != null ? node.regionId() : "cn-default");
    item.put("entitlement", "FREE");
    item.put("link_id", node.linkId() != null ? node.linkId() : "general");
    aiWorkerClient.syncKnowledge(List.of(item));
  }

  private void writeAudit(String actor, String action, String targetType, String targetId, String result, String regionId, String industryId) {
    jdbcTemplate.update("""
        insert into audit_logs (actor, action, target_type, target_id, result, region_id, industry_id)
        values (?, ?, ?, ?, ?, ?, ?)
        """, actor, action, targetType, targetId, result, regionId, industryId);
  }

  private RowMapper<KnowledgeTreeNode> nodeMapper() {
    return (rs, rowNum) -> new KnowledgeTreeNode(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("slug"),
        rs.getString("industry_id"),
        rs.getString("region_id"),
        rs.getString("link_id"),
        rs.getString("status"),
        (Long) rs.getObject("published_version_id"),
        rs.getInt("version_count"),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<KnowledgeVersion> versionMapper() {
    return (rs, rowNum) -> new KnowledgeVersion(
        rs.getLong("id"),
        rs.getLong("node_id"),
        rs.getInt("version_number"),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("content"),
        rs.getString("source_url"),
        rs.getString("review_status"),
        rs.getString("author"),
        rs.getString("reviewer"),
        rs.getString("review_notes"),
        rs.getString("change_notes"),
        rs.getBigDecimal("confidence"),
        rs.getString("created_by_action"),
        timestamp(rs.getTimestamp("create_time")),
        timestamp(rs.getTimestamp("update_time")));
  }

  private RowMapper<KnowledgePublication> publicationMapper() {
    return (rs, rowNum) -> new KnowledgePublication(
        rs.getLong("id"),
        rs.getLong("node_id"),
        rs.getLong("version_id"),
        rs.getString("action"),
        rs.getString("actor"),
        rs.getString("notes"),
        timestamp(rs.getTimestamp("create_time")));
  }

  private LocalDateTime timestamp(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }

  private long generatedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("id") instanceof Number id) {
      return id.longValue();
    }
    return keyHolder.getKey().longValue();
  }

  private String normalizedSlug(KnowledgeDraftRequest request) {
    return normalizedSlug(request.title(), request.slug());
  }

  private String normalizedSlug(String title, String preferredSlug) {
    String source = StringUtils.hasText(preferredSlug) ? preferredSlug : blankToDefault(title, "knowledge-node");
    String normalized = source.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return StringUtils.hasText(normalized) ? normalized : "knowledge-node";
  }

  private String blankToDefault(String value, String fallback) {
    return StringUtils.hasText(value) ? value : fallback;
  }

  AdminKnowledgeDtos.InspectionReport inspect() {
    var nodes = listNodes();
    var findings = new java.util.ArrayList<AdminKnowledgeDtos.InspectionFinding>();
    int warning = 0;
    int critical = 0;

    for (var node : nodes) {
      var detail = detail(node.nodeId());
      var versions = detail.versions();
      var latestVersion = versions.isEmpty() ? null : versions.get(0);

      if (latestVersion != null && (latestVersion.sourceUrl() == null || latestVersion.sourceUrl().isBlank())) {
        findings.add(new AdminKnowledgeDtos.InspectionFinding(
            "NO_SOURCE", node.nodeId(), node.title(),
            "Latest version has no source URL. Add a reference to the original evidence."));
      }

      if (latestVersion != null && latestVersion.confidence() != null && latestVersion.confidence().doubleValue() < 0.7) {
        findings.add(new AdminKnowledgeDtos.InspectionFinding(
            "LOW_CONFIDENCE", node.nodeId(), node.title(),
            "Confidence is below 0.7 (current: " + latestVersion.confidence() + "). Review and update with stronger evidence."));
      }

      if ("DRAFT".equals(node.status()) && node.updateTime() != null &&
          node.updateTime().isBefore(LocalDateTime.now().minusDays(7))) {
        findings.add(new AdminKnowledgeDtos.InspectionFinding(
            "STALE_DRAFT", node.nodeId(), node.title(),
            "Draft has not been updated in over 7 days. Consider submitting for review or discarding."));
      }

      boolean hasConflict = false;
      for (int i = 0; i < versions.size() && !hasConflict; i++) {
        for (int j = i + 1; j < versions.size() && !hasConflict; j++) {
          if ("APPROVED".equals(versions.get(i).reviewStatus()) &&
              "APPROVED".equals(versions.get(j).reviewStatus())) {
            findings.add(new AdminKnowledgeDtos.InspectionFinding(
                "DUPLICATE_APPROVED", node.nodeId(), node.title(),
                "Multiple approved versions exist (V" + versions.get(j).versionNumber() +
                " and V" + versions.get(i).versionNumber() +
                "). Consider publishing one and archiving the other."));
            hasConflict = true;
          }
        }
      }
    }

    for (var finding : findings) {
      if ("STALE_DRAFT".equals(finding.type()) || "DUPLICATE_APPROVED".equals(finding.type())) {
        critical++;
      } else {
        warning++;
      }
    }

    return new AdminKnowledgeDtos.InspectionReport(
        findings, nodes.size(),
        nodes.size() - warning - critical, warning, critical);
  }
}
