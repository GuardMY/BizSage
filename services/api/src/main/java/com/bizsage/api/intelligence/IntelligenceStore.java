package com.bizsage.api.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceStore {
  private final AtomicLong ids = new AtomicLong(1);
  private final List<IntelligenceItem> items = new ArrayList<>();

  public synchronized IntelligenceItem create(CreateIntelligenceRequest request) {
    IntelligenceItem item = new IntelligenceItem(
        ids.getAndIncrement(),
        request.title(),
        request.content(),
        request.url(),
        "PENDING",
        0.6,
        request.linkId(),
        request.regionId(),
        request.industryId(),
        request.sourceId(),
        0.6);
    items.add(item);
    return item;
  }

  public synchronized IntelligenceItem approve(long id) {
    for (int i = 0; i < items.size(); i++) {
      IntelligenceItem item = items.get(i);
      if (item.id() == id) {
        IntelligenceItem approved = item.approve();
        items.set(i, approved);
        return approved;
      }
    }
    throw new IllegalArgumentException("intelligence not found");
  }

  public synchronized List<IntelligenceItem> list() {
    return List.copyOf(items);
  }
}
