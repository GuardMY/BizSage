package com.bizsage.api.reports;

import com.bizsage.api.intelligence.PaidIntelligenceStore;
import com.bizsage.api.users.UserAccount;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisReportService {
  private final PaidIntelligenceStore paidIntelligenceStore;

  public DiagnosisReportService(PaidIntelligenceStore paidIntelligenceStore) {
    this.paidIntelligenceStore = paidIntelligenceStore;
  }

  public DiagnosisReport build(String question, UserAccount user) {
    List<ReportSource> sources = new ArrayList<>();
    sources.add(new ReportSource(
        "seed-restaurant-cashflow",
        "Restaurant cashflow baseline",
        "seed://v1/restaurant-cashflow",
        "seed-baseline",
        0.9,
        "FREE"));
    paidIntelligenceStore.listFor(user).stream()
        .filter(item -> item.status().equals("APPROVED"))
        .map(item -> new ReportSource(
            "paid-" + item.id(),
            item.title(),
            item.url(),
            item.sourceId(),
            item.confidence(),
            item.entitlement()))
        .forEach(sources::add);

    return new DiagnosisReport(
        "PDF",
        question,
        "V2 operating diagnosis report for gray release. Evidence is filtered by user entitlement.",
        List.copyOf(sources),
        "MEDIUM",
        "Generated from V2 gray-release knowledge and approved intelligence snapshots.",
        "PASSED",
        "Disclaimer: This report is for operational analysis only and is not legal, financial, or investment advice.");
  }
}
