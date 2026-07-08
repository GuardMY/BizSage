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
 * Bulk-loads all published knowledge from MySQL into the AI worker's Qdrant
 * vector store on application startup.
 *
 * <p>This ensures the persistent Qdrant collection is populated before the
 * first diagnosis request.  Subsequent publishes and rollbacks are synced
 * incrementally via {@link AdminKnowledgeStore#publish}.
 *
 * <p>Failures are logged but do not prevent application startup — the
 * knowledge will be synced on the next publish event or the next restart.
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
      // Check that the AI worker is reachable before attempting sync
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

  private List<Map<String, Object>> loadAllKnowledge() {
    List<Map<String, Object>> combined = new ArrayList<>();

    // Load all knowledge_items (no scope filter — admin view)
    List<KnowledgeItem> knowledgeItems = knowledgeStore.listScoped(null, null);
    for (KnowledgeItem item : knowledgeItems) {
      combined.add(knowledgeToMap(item));
    }

    // Load all approved intelligence (unrestricted scope)
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
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
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
    map.put("industry_id", item.industryId() != null ? item.industryId() : "general");
    map.put("region_id", item.regionId() != null ? item.regionId() : "cn-default");
    map.put("entitlement", "FREE");
    return map;
  }
}
