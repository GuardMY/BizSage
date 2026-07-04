package com.bizsage.api.reports;

public record ReportSource(
    String id,
    String title,
    String sourceUrl,
    String sourceId,
    double confidence,
    String entitlement) {
}
