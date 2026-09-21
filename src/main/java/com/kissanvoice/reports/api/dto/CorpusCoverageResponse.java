package com.kissanvoice.reports.api.dto;

import java.time.Instant;
import java.util.List;

/** Consumed by the nightly n8n schedule workflow (integration/n8n/workflows/nightly-corpus-report.json). */
public record CorpusCoverageResponse(
        long totalQuestions,
        long answeredQuestions,
        double coveragePercent,
        long totalContributors,
        long totalAcceptedRecordings,
        List<CategoryCoverage> categories,
        Instant generatedAt
) {
    public record CategoryCoverage(
            String category,
            long totalQuestions,
            long answeredQuestions,
            double coveragePercent
    ) {
    }
}
