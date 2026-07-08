package com.bizsage.api.memory;

import com.bizsage.api.worker.AiWorkerClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that syncs PENDING user memory embeddings to Qdrant.
 *
 * <p>Runs every 30 seconds, picking up to 50 PENDING records per batch.
 * Each record's memory_text is sent to the AI worker, which computes
 * an embedding and upserts it to the bizsage_memory Qdrant collection.
 * On success the record is marked SYNCED; on failure it is marked FAILED.
 */
@Component
public class MemorySyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(MemorySyncScheduler.class);
  private static final int BATCH_SIZE = 50;

  private final UserMemoryEmbeddingStore embeddingStore;
  private final AiWorkerClient aiWorkerClient;

  public MemorySyncScheduler(UserMemoryEmbeddingStore embeddingStore, AiWorkerClient aiWorkerClient) {
    this.embeddingStore = embeddingStore;
    this.aiWorkerClient = aiWorkerClient;
  }

  /** Every 30 seconds — pick up pending memory embeddings and sync them to Qdrant. */
  @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
  public void syncPendingEmbeddings() {
    List<Map<String, Object>> pending = embeddingStore.listPending(BATCH_SIZE);
    if (pending.isEmpty()) {
      return;
    }

    log.debug("Found {} pending memory embeddings to sync", pending.size());

    // Map DB columns to the format expected by the AI worker's /memory/sync endpoint
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

    // Update status for each result
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

    // Mark any records that weren't in the results as failed
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
