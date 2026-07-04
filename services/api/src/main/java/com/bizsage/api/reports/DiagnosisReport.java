package com.bizsage.api.reports;

import java.util.List;

public record DiagnosisReport(
    String format,
    String question,
    String summary,
    List<ReportSource> sources,
    String confidence,
    String timeliness,
    String selfCheckStatus,
    String disclaimer) {
}
