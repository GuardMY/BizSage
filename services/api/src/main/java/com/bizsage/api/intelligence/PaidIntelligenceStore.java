package com.bizsage.api.intelligence;

import com.bizsage.api.users.UserAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class PaidIntelligenceStore {
  private final AtomicLong ids = new AtomicLong(1);
  private final List<PaidIntelligenceItem> items = new ArrayList<>();

  public synchronized PaidIntelligenceItem create(CreateIntelligenceRequest request) {
    PaidIntelligenceItem item = new PaidIntelligenceItem(
        ids.getAndIncrement(),
        request.title(),
        request.content(),
        request.url(),
        "PENDING",
        0.75,
        request.linkId(),
        request.regionId(),
        request.industryId(),
        request.sourceId(),
        0.9,
        "PAID");
    items.add(item);
    return item;
  }

  public synchronized PaidIntelligenceItem approve(long id) {
    for (int i = 0; i < items.size(); i++) {
      PaidIntelligenceItem item = items.get(i);
      if (item.id() == id) {
        PaidIntelligenceItem approved = item.approve();
        items.set(i, approved);
        return approved;
      }
    }
    throw new IllegalArgumentException("paid intelligence not found");
  }

  public synchronized List<PaidIntelligenceItem> listFor(UserAccount user) {
    if (user.role().name().equals("SUPER_ADMIN") || user.role().name().equals("OPERATOR")) {
      return List.copyOf(items);
    }
    if (!"SEED_PAID".equals(user.membershipLevel())) {
      return List.of();
    }
    return items.stream()
        .filter(item -> item.status().equals("APPROVED"))
        .filter(item -> item.regionId().equals(user.regionId()))
        .filter(item -> item.industryId().equals(user.industryId()))
        .toList();
  }
}
