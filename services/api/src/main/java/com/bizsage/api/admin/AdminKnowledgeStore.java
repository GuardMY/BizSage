package com.bizsage.api.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeDraftRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeNodeDetail;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgePublication;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeRollbackRequest;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeTreeNode;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeVersion;
import com.bizsage.api.admin.AdminKnowledgeDtos.KnowledgeVersionDiff;
import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeMapper;
import com.bizsage.api.worker.AiWorkerClient;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminKnowledgeStore {
  private final AdminKnowledgeMapper mapper;
  private final KnowledgeMapper knowledgeMapper;
  private final AiWorkerClient aiWorkerClient;

  public AdminKnowledgeStore(AdminKnowledgeMapper mapper, KnowledgeMapper knowledgeMapper, AiWorkerClient aiWorkerClient) {
    this.mapper = mapper;
    this.knowledgeMapper = knowledgeMapper;
    this.aiWorkerClient = aiWorkerClient;
  }

  List<KnowledgeTreeNode> listNodes() {
    return mapper.listNodes().stream().map(this::toNode).toList();
  }

  KnowledgeNodeDetail detail(long nodeId) {
    KnowledgeTreeNode node = toNode(requireMap(mapper.findNode(nodeId), "knowledge node not found"));
    List<KnowledgeVersion> versions = mapper.listVersions(nodeId).stream().map(this::toVersion).toList();
    List<KnowledgePublication> publications = mapper.listPublications(nodeId).stream().map(this::toPublication).toList();

    Long draftVersionId = versions.stream().filter(version -> "DRAFT".equals(version.reviewStatus())).map(KnowledgeVersion::versionId).findFirst().orElse(null);
    Long reviewVersionId = versions.stream().filter(version -> "IN_REVIEW".equals(version.reviewStatus())).map(KnowledgeVersion::versionId).findFirst().orElse(null);

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
    int nextVersion = intValue(mapper.nextVersionNumber(nodeId), 1);

    Map<String, Object> versionValues = new LinkedHashMap<>();
    versionValues.put("nodeId", nodeId);
    versionValues.put("versionNumber", nextVersion);
    versionValues.put("title", blankToDefault(request.title(), "Untitled node"));
    versionValues.put("summary", blankToDefault(request.summary(), ""));
    versionValues.put("content", blankToDefault(request.content(), ""));
    versionValues.put("sourceUrl", request.sourceUrl());
    versionValues.put("author", actor);
    versionValues.put("confidence", request.confidence() == null ? BigDecimal.valueOf(0.85) : request.confidence());
    versionValues.put("changeNotes", blankToDefault(request.changeNotes(), "Draft saved"));
    mapper.insertVersion(versionValues);
    long versionId = longValue(versionValues.get("id"));

    mapper.updateNodeForDraft(Map.of(
        "nodeId", nodeId,
        "title", blankToDefault(request.title(), "Untitled node"),
        "slug", normalizedSlug(request),
        "industryId", blankToDefault(request.industryId(), "general"),
        "regionId", blankToDefault(request.regionId(), "cn-default"),
        "linkId", blankToDefault(request.linkId(), "general"),
        "draftVersionId", versionId));

    writeAudit(actor, "ADMIN_KNOWLEDGE_SAVE_DRAFT", "knowledge_node", String.valueOf(nodeId), "SUCCESS", blankToDefault(request.regionId(), "cn-default"), blankToDefault(request.industryId(), "general"));
    return detail(nodeId);
  }

  KnowledgeNodeDetail submitReview(long nodeId, long versionId, String notes, String actor) {
    KnowledgeVersion version = requireVersion(nodeId, versionId);
    if (!"DRAFT".equals(version.reviewStatus())) {
      throw new IllegalArgumentException("only draft versions can enter review");
    }
    mapper.updateVersionReview(versionId, "IN_REVIEW", null, blankToDefault(notes, "Submitted for review"));
    mapper.updateNodeReviewState(nodeId, "IN_REVIEW", null, versionId);
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
    mapper.updateVersionReview(versionId, "APPROVED", actor, blankToDefault(notes, "Approved"));
    mapper.updateNodeReviewState(nodeId, "APPROVED", null, versionId);
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
    mapper.publishNode(nodeId, version.title(), normalizedSlug(version.title(), current.slug()), versionId);
    mapper.insertPublication(nodeId, versionId, "PUBLISH", actor, blankToDefault(notes, "Published"));
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
    mapper.publishNode(nodeId, version.title(), normalizedSlug(version.title(), current.slug()), version.versionId());
    mapper.insertPublication(nodeId, version.versionId(), "ROLLBACK", actor, blankToDefault(request.notes(), "Rolled back"));
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
        left.versionId(), right.versionId(), left.title(), right.title(), left.summary(), right.summary(),
        left.content(), right.content(), left.sourceUrl(), right.sourceUrl(), left.confidence(), right.confidence(),
        left.reviewStatus(), right.reviewStatus());
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
        findings.add(new AdminKnowledgeDtos.InspectionFinding("NO_SOURCE", node.nodeId(), node.title(), "Latest version has no source URL. Add a reference to the original evidence."));
      }
      if (latestVersion != null && latestVersion.confidence() != null && latestVersion.confidence().doubleValue() < 0.7) {
        findings.add(new AdminKnowledgeDtos.InspectionFinding("LOW_CONFIDENCE", node.nodeId(), node.title(), "Confidence is below 0.7 (current: " + latestVersion.confidence() + "). Review and update with stronger evidence."));
      }
      if ("DRAFT".equals(node.status()) && node.updateTime() != null && node.updateTime().isBefore(LocalDateTime.now().minusDays(7))) {
        findings.add(new AdminKnowledgeDtos.InspectionFinding("STALE_DRAFT", node.nodeId(), node.title(), "Draft has not been updated in over 7 days. Consider submitting for review or discarding."));
      }

      boolean hasConflict = false;
      for (int i = 0; i < versions.size() && !hasConflict; i++) {
        for (int j = i + 1; j < versions.size() && !hasConflict; j++) {
          if ("APPROVED".equals(versions.get(i).reviewStatus()) && "APPROVED".equals(versions.get(j).reviewStatus())) {
            findings.add(new AdminKnowledgeDtos.InspectionFinding("DUPLICATE_APPROVED", node.nodeId(), node.title(), "Multiple approved versions exist (V" + versions.get(j).versionNumber() + " and V" + versions.get(i).versionNumber() + "). Consider publishing one and archiving the other."));
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

    return new AdminKnowledgeDtos.InspectionReport(findings, nodes.size(), nodes.size() - warning - critical, warning, critical);
  }

  private long createNode(KnowledgeDraftRequest request, String actor) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("title", blankToDefault(request.title(), "Untitled node"));
    values.put("slug", normalizedSlug(request));
    values.put("industryId", blankToDefault(request.industryId(), "general"));
    values.put("regionId", blankToDefault(request.regionId(), "cn-default"));
    values.put("linkId", blankToDefault(request.linkId(), "general"));
    mapper.insertNode(values);
    long nodeId = longValue(values.get("id"));
    writeAudit(actor, "ADMIN_KNOWLEDGE_CREATE_NODE", "knowledge_node", String.valueOf(nodeId), "SUCCESS", blankToDefault(request.regionId(), "cn-default"), blankToDefault(request.industryId(), "general"));
    return nodeId;
  }

  private KnowledgeVersion requireVersion(long nodeId, long versionId) {
    KnowledgeVersion version = findVersionById(versionId);
    if (version.nodeId() != nodeId) {
      throw new IllegalArgumentException("knowledge version does not belong to node");
    }
    return version;
  }

  private KnowledgeVersion findVersionById(long versionId) {
    return toVersion(requireMap(mapper.findVersion(versionId), "knowledge version not found"));
  }

  private void syncPublishedVersion(KnowledgeNodeDetail node, KnowledgeVersion version) {
    KnowledgeItem existing = knowledgeMapper.selectOne(new LambdaQueryWrapper<KnowledgeItem>()
        .eq(KnowledgeItem::getSourceId, "admin-node-" + node.nodeId())
        .last("limit 1"));

    if (existing == null) {
      KnowledgeItem item = new KnowledgeItem();
      item.setTitle(version.title());
      item.setContent(version.content());
      item.setSourceUrl(version.sourceUrl());
      item.setConfidence(version.confidence() == null ? null : version.confidence().doubleValue());
      item.setLinkId(node.linkId());
      item.setRegionId(node.regionId());
      item.setIndustryId(node.industryId());
      item.setSourceId("admin-node-" + node.nodeId());
      item.setWeight(version.confidence() == null ? null : version.confidence().doubleValue());
      knowledgeMapper.insert(item);
    } else {
      knowledgeMapper.update(null, new LambdaUpdateWrapper<KnowledgeItem>()
          .eq(KnowledgeItem::getId, existing.getId())
          .set(KnowledgeItem::getTitle, version.title())
          .set(KnowledgeItem::getContent, version.content())
          .set(KnowledgeItem::getSourceUrl, version.sourceUrl())
          .set(KnowledgeItem::getConfidence, version.confidence() == null ? null : version.confidence().doubleValue())
          .set(KnowledgeItem::getLinkId, node.linkId())
          .set(KnowledgeItem::getRegionId, node.regionId())
          .set(KnowledgeItem::getIndustryId, node.industryId())
          .set(KnowledgeItem::getWeight, version.confidence() == null ? null : version.confidence().doubleValue()));
    }
  }

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
    mapper.insertAuditLog(Map.of(
        "actor", actor,
        "action", action,
        "targetType", targetType,
        "targetId", targetId,
        "result", result,
        "regionId", regionId,
        "industryId", industryId));
  }

  private KnowledgeTreeNode toNode(Map<String, Object> row) {
    return new KnowledgeTreeNode(
        longValue(row, "nodeId"),
        stringValue(row, "title"),
        stringValue(row, "slug"),
        stringValue(row, "industryId"),
        stringValue(row, "regionId"),
        stringValue(row, "linkId"),
        stringValue(row, "status"),
        nullableLong(row, "publishedVersionId"),
        intValue(lookup(row, "versionCount"), 0),
        timeValue(row, "updateTime"));
  }

  private KnowledgeVersion toVersion(Map<String, Object> row) {
    return new KnowledgeVersion(
        longValue(row, "versionId"),
        longValue(row, "nodeId"),
        intValue(lookup(row, "versionNumber"), 0),
        stringValue(row, "title"),
        stringValue(row, "summary"),
        stringValue(row, "content"),
        stringValue(row, "sourceUrl"),
        stringValue(row, "reviewStatus"),
        stringValue(row, "author"),
        stringValue(row, "reviewer"),
        stringValue(row, "reviewNotes"),
        stringValue(row, "changeNotes"),
        decimalValue(lookup(row, "confidence")),
        stringValue(row, "createdByAction"),
        timeValue(row, "createTime"),
        timeValue(row, "updateTime"));
  }

  private KnowledgePublication toPublication(Map<String, Object> row) {
    return new KnowledgePublication(
        longValue(row, "publicationId"),
        longValue(row, "nodeId"),
        longValue(row, "versionId"),
        stringValue(row, "action"),
        stringValue(row, "actor"),
        stringValue(row, "notes"),
        timeValue(row, "createTime"));
  }

  private Map<String, Object> requireMap(Map<String, Object> row, String message) {
    if (row == null || row.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return row;
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

  private int intValue(Object value, int fallback) {
    return value instanceof Number number ? number.intValue() : fallback;
  }

  private BigDecimal decimalValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private LocalDateTime timeValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
    return value instanceof LocalDateTime time ? time : null;
  }

  private String stringValue(Map<String, Object> row, String key) {
    Object value = lookup(row, key);
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
}
