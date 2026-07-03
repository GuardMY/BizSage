package com.bizsage.api.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeStore {
  private final AtomicLong ids = new AtomicLong(1);
  private final List<KnowledgeItem> items = new ArrayList<>();

  public synchronized KnowledgeItem importItem(ImportKnowledgeRequest request) {
    KnowledgeItem item = new KnowledgeItem(
        ids.getAndIncrement(),
        request.title(),
        request.content(),
        request.industryId(),
        request.regionId(),
        request.linkId(),
        request.sourceId(),
        0.85,
        0.85);
    items.add(item);
    return item;
  }

  public synchronized List<KnowledgeItem> list() {
    return List.copyOf(items);
  }
}
