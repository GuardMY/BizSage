package com.bizsage.api.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AdminCollectionScheduler {
  private final AdminCollectionStore store;
  private final boolean schedulerEnabled;

  public AdminCollectionScheduler(
      AdminCollectionStore store,
      @Value("${bizsage.admin.collection.scheduler-enabled:true}") boolean schedulerEnabled) {
    this.store = store;
    this.schedulerEnabled = schedulerEnabled;
  }

  @Scheduled(fixedDelayString = "${bizsage.admin.collection.scheduler-delay-ms:15000}")
  void runDueSources() {
    if (!schedulerEnabled) {
      return;
    }
    store.runDueSources();
  }
}
