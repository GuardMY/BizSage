package com.bizsage.api.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bizsage.api.governance.DataScope;
import com.bizsage.api.worker.AiWorkerClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceStore {
  private static final Logger log = LoggerFactory.getLogger(IntelligenceStore.class);

  private final IntelligenceMapper intelligenceMapper;
  private final AiWorkerClient aiWorkerClient;

  public IntelligenceStore(IntelligenceMapper intelligenceMapper, AiWorkerClient aiWorkerClient) {
    this.intelligenceMapper = intelligenceMapper;
    this.aiWorkerClient = aiWorkerClient;
  }

  public IntelligenceItem create(CreateIntelligenceRequest request) {
    IntelligenceItem item = new IntelligenceItem();
    item.setTitle(request.title());
    item.setContent(request.content());
    item.setUrl(request.url());
    item.setStatus("PENDING");
    item.setConfidence(0.6D);
    item.setLinkId(request.linkId());
    item.setRegionId(request.regionId());
    item.setIndustryId(request.industryId());
    item.setSourceId(request.sourceId());
    item.setWeight(0.6D);
    item.setEntitlement("FREE");
    item.setContentHash(sha256(request.content()));
    intelligenceMapper.insert(item);
    return find(item.id());
  }

  public IntelligenceItem createFromRecord(Map<String, Object> record) {
    String content = stringField(record, "content", "");
    IntelligenceItem item = new IntelligenceItem();
    item.setTitle(stringField(record, "title", "Untitled"));
    item.setContent(content);
    item.setUrl(stringField(record, "url", null));
    item.setStatus("PENDING");
    item.setConfidence(doubleField(record, "confidence", 0.6));
    item.setLinkId(stringField(record, "link_id", "collection"));
    item.setRegionId(stringField(record, "region_id", "cn-default"));
    item.setIndustryId(stringField(record, "industry_id", "general"));
    item.setSourceId(stringField(record, "source_id", "collector"));
    item.setWeight(doubleField(record, "weight", 0.6));
    item.setEntitlement("FREE");
    item.setContentHash(sha256(content));
    intelligenceMapper.insert(item);
    return find(item.id());
  }

  public List<IntelligenceItem> bulkCreateFromRecords(List<Map<String, Object>> records) {
    List<IntelligenceItem> created = new ArrayList<>();
    for (Map<String, Object> record : records) {
      try {
        created.add(createFromRecord(record));
      } catch (Exception ex) {
        log.warn("Failed to create intelligence from record: {}", ex.getMessage());
      }
    }
    return created;
  }

  public IntelligenceItem approve(long id) {
    int updated = intelligenceMapper.update(null, new LambdaUpdateWrapper<IntelligenceItem>()
        .eq(IntelligenceItem::getId, id)
        .set(IntelligenceItem::getStatus, "APPROVED"));
    if (updated == 0) {
      throw new IllegalArgumentException("intelligence not found");
    }
    IntelligenceItem item = find(id);
    syncToQdrant(item);
    return item;
  }

  public List<IntelligenceItem> list() {
    return intelligenceMapper.selectList(new LambdaQueryWrapper<IntelligenceItem>()
        .orderByAsc(IntelligenceItem::getId));
  }

  public List<IntelligenceItem> listScoped(DataScope scope) {
    if (scope.isAdmin()) {
      return list();
    }
    LambdaQueryWrapper<IntelligenceItem> wrapper = new LambdaQueryWrapper<>();
    if (scope.regionId() != null) {
      wrapper.eq(IntelligenceItem::getRegionId, scope.regionId());
    }
    if (scope.industryId() != null) {
      wrapper.eq(IntelligenceItem::getIndustryId, scope.industryId());
    }
    if (!scope.canAccessPaid()) {
      wrapper.and(w -> w.notLike(IntelligenceItem::getUrl, "paid").or().isNull(IntelligenceItem::getUrl));
    }
    wrapper.orderByAsc(IntelligenceItem::getId);
    return intelligenceMapper.selectList(wrapper);
  }

  public List<IntelligenceItem> listApprovedScoped(DataScope scope) {
    LambdaQueryWrapper<IntelligenceItem> wrapper = new LambdaQueryWrapper<IntelligenceItem>()
        .eq(IntelligenceItem::getStatus, "APPROVED");
    if (!scope.isAdmin()) {
      if (scope.regionId() != null) {
        wrapper.eq(IntelligenceItem::getRegionId, scope.regionId());
      }
      if (scope.industryId() != null) {
        wrapper.eq(IntelligenceItem::getIndustryId, scope.industryId());
      }
      if (!scope.canAccessPaid()) {
        wrapper.and(w -> w.notLike(IntelligenceItem::getUrl, "paid").or().isNull(IntelligenceItem::getUrl));
      }
    }
    wrapper.orderByAsc(IntelligenceItem::getId);
    return intelligenceMapper.selectList(wrapper);
  }

  private IntelligenceItem find(long id) {
    IntelligenceItem item = intelligenceMapper.selectById(id);
    if (item == null) {
      throw new IllegalArgumentException("intelligence not found");
    }
    return item;
  }

  private void syncToQdrant(IntelligenceItem item) {
    try {
      Map<String, Object> knowledgeItem = new LinkedHashMap<>();
      knowledgeItem.put("id", "intel-" + item.id());
      knowledgeItem.put("title", item.title());
      knowledgeItem.put("content", item.content());
      knowledgeItem.put("source_url", item.url() != null ? item.url() : "");
      knowledgeItem.put("source_id", item.sourceId() != null ? item.sourceId() : "intelligence");
      knowledgeItem.put("weight", item.weight());
      knowledgeItem.put("confidence", item.confidence());
      knowledgeItem.put("authority", 0.85);
      knowledgeItem.put("timeliness", 0.85);
      knowledgeItem.put("industry_id", item.industryId() != null ? item.industryId() : "general");
      knowledgeItem.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
      knowledgeItem.put("entitlement", item.entitlement() != null ? item.entitlement() : "FREE");
      aiWorkerClient.syncKnowledge(List.of(knowledgeItem));
    } catch (Exception ex) {
      log.warn("Failed to sync approved intelligence {} to Qdrant (non-fatal): {}", item.id(), ex.getMessage());
    }
  }

  private static String stringField(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value instanceof String str ? str : defaultValue;
  }

  private static double doubleField(Map<String, Object> map, String key, double defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number num) {
      return num.doubleValue();
    }
    return defaultValue;
  }

  static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
