package com.bizsage.api.worker;

import com.bizsage.api.governance.DataScope;
import com.bizsage.api.intelligence.IntelligenceItem;
import com.bizsage.api.intelligence.IntelligenceStore;
import com.bizsage.api.knowledge.KnowledgeItem;
import com.bizsage.api.knowledge.KnowledgeStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动时将 MySQL 中已发布知识批量加载到 AI Worker 的 Qdrant 向量库。
 *
 * <p>这样可以在第一次诊断前尽量保证持久向量集合已经有完整 RAG 语料。后续发布、回滚
 * 或情报审批会通过增量同步补齐。
 *
 * <p>同步失败只记录日志，不阻止 API 启动；下一次发布事件或重启全量同步会再次修复索引。
 */
@Component
public class KnowledgeSyncInitializer {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncInitializer.class);

  private final AiWorkerClient aiWorkerClient;
  private final KnowledgeStore knowledgeStore;
  private final IntelligenceStore intelligenceStore;

  public KnowledgeSyncInitializer(
      AiWorkerClient aiWorkerClient,
      KnowledgeStore knowledgeStore,
      IntelligenceStore intelligenceStore) {
    this.aiWorkerClient = aiWorkerClient;
    this.knowledgeStore = knowledgeStore;
    this.intelligenceStore = intelligenceStore;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    try {
      // 先探测 Worker 健康状态，避免启动期连接错误刷屏。
      if (!aiWorkerClient.isHealthy()) {
        log.warn("AI worker is not reachable — skipping knowledge sync on startup. "
            + "Knowledge will be synced on next publish or restart.");
        return;
      }

      List<Map<String, Object>> allKnowledge = loadAllKnowledge();
      if (allKnowledge.isEmpty()) {
        log.info("No knowledge items to sync on startup");
        return;
      }

      int count = aiWorkerClient.syncAllKnowledge(allKnowledge);
      log.info("Startup knowledge sync complete: {} items loaded into Qdrant", count);
    } catch (Exception ex) {
      log.error("Startup knowledge sync failed (non-fatal): {}", ex.getMessage(), ex);
    }
  }

  /**
   * 将单条已审批情报实时同步到线上 RAG 语料。
   *
   * <p>运营审批通过后调用，使新情报无需等待重启或批处理即可进入可检索范围。
   */
  public void syncSingle(IntelligenceItem item) {
    if (!"APPROVED".equals(item.status())) {
      log.debug("Skipping syncSingle for non-approved intelligence #{} (status={})",
          item.id(), item.status());
      return;
    }
    try {
      Map<String, Object> payload = intelligenceToMap(item);
      // 复用批量同步端点，单条情报作为一批发送。
      int count = aiWorkerClient.syncAllKnowledge(List.of(payload));
      log.info("Real-time KB sync: intelligence #{} \"{}\" indexed ({} items)",
          item.id(), item.title(), count);
    } catch (Exception ex) {
      log.error("Real-time KB sync failed for intelligence #{} (will retry on next batch): {}",
          item.id(), ex.getMessage(), ex);
    }
  }

  private List<Map<String, Object>> loadAllKnowledge() {
    List<Map<String, Object>> combined = new ArrayList<>();

    // 启动全量同步使用管理员视角，不按地域/行业过滤。
    List<KnowledgeItem> knowledgeItems = knowledgeStore.listScoped(null, null);
    for (KnowledgeItem item : knowledgeItems) {
      combined.add(knowledgeToMap(item));
    }

    // 已审批情报也使用无限制 scope，Worker 侧检索时再按用户权限过滤。
    List<IntelligenceItem> intelligenceItems =
        intelligenceStore.listApprovedScoped(DataScope.unrestricted());
    for (IntelligenceItem item : intelligenceItems) {
      combined.add(intelligenceToMap(item));
    }

    return combined;
  }

  private Map<String, Object> knowledgeToMap(KnowledgeItem item) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "kb-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.sourceUrl() != null ? item.sourceUrl() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "knowledge");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("authority", 0.85);    // 六维重排默认权威度。
    map.put("timeliness", 0.85);   // 六维重排默认时效性。
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", item.entitlement() != null ? item.entitlement() : "FREE");
    return map;
  }

  private Map<String, Object> intelligenceToMap(IntelligenceItem item) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", "intel-" + item.id());
    map.put("title", item.title());
    map.put("content", item.content());
    map.put("source_url", item.url() != null ? item.url() : "");
    map.put("source_id", item.sourceId() != null ? item.sourceId() : "intelligence");
    map.put("weight", item.weight());
    map.put("confidence", item.confidence());
    map.put("authority", 0.85);    // 六维重排默认权威度。
    map.put("timeliness", 0.85);   // 六维重排默认时效性。
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", item.entitlement() != null ? item.entitlement() : "FREE");
    return map;
  }
}
