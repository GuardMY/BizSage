package com.bizsage.api.memory;

import com.bizsage.api.worker.AiWorkerClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时同步 PENDING 用户记忆向量到 Qdrant。
 *
 * <p>每 30 秒最多捞取 50 条 PENDING 记录，把 memory_text 发送给 AI Worker。
 * Worker 负责计算向量并写入 bizsage_memory 集合；成功后标记 SYNCED，未返回的记录标记 FAILED。
 */
@Component
@ConditionalOnProperty(name = "bizsage.memory.vector-sync-enabled", havingValue = "true")
public class MemorySyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(MemorySyncScheduler.class);
  private static final int BATCH_SIZE = 50;

  private final UserMemoryEmbeddingStore embeddingStore;
  private final AiWorkerClient aiWorkerClient;

  public MemorySyncScheduler(UserMemoryEmbeddingStore embeddingStore, AiWorkerClient aiWorkerClient) {
    this.embeddingStore = embeddingStore;
    this.aiWorkerClient = aiWorkerClient;
  }

  /** 每 30 秒捞取待同步记忆并写入 Qdrant。 */
  @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
  public void syncPendingEmbeddings() {
    List<Map<String, Object>> pending = embeddingStore.listPending(BATCH_SIZE);
    if (pending.isEmpty()) {
      return;
    }

    log.debug("Found {} pending memory embeddings to sync", pending.size());

    // 将数据库列名映射为 AI Worker /memory/sync 端点期望的字段名。
    List<Map<String, Object>> requestMemories = pending.stream()
        .map(row -> {
          Map<String, Object> mem = new HashMap<>();
          mem.put("id", row.get("id"));
          mem.put("memory_text", row.get("memory_text"));
          mem.put("user_id", row.get("user_id"));
          mem.put("source_conversation_id", row.get("source_conversation_id"));
          return mem;
        })
        .toList();

    List<Map<String, Object>> results = aiWorkerClient.syncMemoryEmbeddings(requestMemories);

    // Worker 返回结果中的记录标记为 SYNCED。
    for (Map<String, Object> result : results) {
      try {
        Object embeddingIdObj = result.get("embedding_id");
        Object qdrantPointIdObj = result.get("qdrant_point_id");
        if (embeddingIdObj instanceof Number embeddingId && qdrantPointIdObj instanceof String qdrantPointId) {
          embeddingStore.markSynced(embeddingId.longValue(), qdrantPointId);
        }
      } catch (Exception ex) {
        log.warn("Failed to mark memory embedding as synced: {}", ex.getMessage());
      }
    }

    // 本批次中未出现在结果里的记录标记为 FAILED，等待后续人工或重试策略处理。
    if (results.size() < pending.size()) {
      var syncedIds = results.stream()
          .map(r -> r.get("embedding_id"))
          .filter(id -> id instanceof Number)
          .map(id -> ((Number) id).longValue())
          .collect(java.util.stream.Collectors.toSet());

      for (Map<String, Object> row : pending) {
        Object idObj = row.get("id");
        if (idObj instanceof Number id && !syncedIds.contains(id.longValue())) {
          try {
            embeddingStore.markFailed(id.longValue());
          } catch (Exception ex) {
            log.warn("Failed to mark memory embedding as failed: {}", ex.getMessage());
          }
        }
      }
    }

    log.info("Memory embedding sync batch complete: {} synced, {} total",
        results.size(), pending.size());
  }
}
