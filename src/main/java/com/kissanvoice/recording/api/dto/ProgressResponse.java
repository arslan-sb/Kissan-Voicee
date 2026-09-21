package com.kissanvoice.recording.api.dto;

import java.util.UUID;

public record ProgressResponse(
        UUID contributorId,
        long answered,
        long remaining,
        long corpusSize,
        double percentComplete,
        boolean complete
) {
    public static ProgressResponse of(UUID contributorId, long answered, long remaining, long corpusSize) {
        double pct = corpusSize == 0 ? 0d
                : Math.round((answered * 10000d) / corpusSize) / 100d;
        return new ProgressResponse(contributorId, answered, remaining, corpusSize, pct, remaining == 0);
    }
}
