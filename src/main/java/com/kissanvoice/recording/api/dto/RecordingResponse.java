package com.kissanvoice.recording.api.dto;

import com.kissanvoice.recording.domain.Recording;

import java.time.Instant;
import java.util.UUID;

public record RecordingResponse(
        UUID id,
        UUID contributorId,
        UUID questionId,
        UUID sessionId,
        String mediaKey,
        String contentType,
        long sizeBytes,
        Integer durationMs,
        String status,
        Instant createdAt
) {
    public static RecordingResponse from(Recording r) {
        return new RecordingResponse(r.getId(), r.getContributorId(), r.getQuestionId(),
                r.getSessionId(), r.getMediaKey(), r.getContentType(), r.getSizeBytes(),
                r.getDurationMs(), r.getStatus().name(), r.getCreatedAt());
    }
}
