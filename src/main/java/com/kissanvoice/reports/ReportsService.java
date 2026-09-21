package com.kissanvoice.reports;

import com.kissanvoice.contributor.ContributorRepository;
import com.kissanvoice.corpus.CategoryCoverageRow;
import com.kissanvoice.corpus.QuestionRepository;
import com.kissanvoice.recording.RecordingRepository;
import com.kissanvoice.recording.domain.RecordingStatus;
import com.kissanvoice.reports.api.dto.CorpusCoverageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * A read model over the contributor/corpus/recording aggregates - it never
 * writes to any of them. Exists to answer "how is the corpus doing overall",
 * which none of the per-contributor services need to know how to do.
 */
@Service
@Transactional(readOnly = true)
public class ReportsService {

    private final QuestionRepository questions;
    private final RecordingRepository recordings;
    private final ContributorRepository contributors;

    public ReportsService(QuestionRepository questions, RecordingRepository recordings,
                          ContributorRepository contributors) {
        this.questions = questions;
        this.recordings = recordings;
        this.contributors = contributors;
    }

    public CorpusCoverageResponse corpusCoverage() {
        List<CorpusCoverageResponse.CategoryCoverage> categories = questions.coverageByCategory().stream()
                .map(this::toCategoryCoverage)
                .toList();

        long totalQuestions = categories.stream().mapToLong(CorpusCoverageResponse.CategoryCoverage::totalQuestions).sum();
        long answeredQuestions = categories.stream().mapToLong(CorpusCoverageResponse.CategoryCoverage::answeredQuestions).sum();

        return new CorpusCoverageResponse(
                totalQuestions,
                answeredQuestions,
                percent(answeredQuestions, totalQuestions),
                contributors.count(),
                recordings.countByStatus(RecordingStatus.ACCEPTED),
                categories,
                Instant.now());
    }

    private CorpusCoverageResponse.CategoryCoverage toCategoryCoverage(CategoryCoverageRow row) {
        return new CorpusCoverageResponse.CategoryCoverage(
                row.getCategory(), row.getTotalQuestions(), row.getAnsweredQuestions(),
                percent(row.getAnsweredQuestions(), row.getTotalQuestions()));
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0d : Math.round((part * 10000d) / whole) / 100d;
    }
}
