package com.kissanvoice.outbox.events;

import java.util.UUID;

/** Payload {@code data} for the {@code RecordingCaptured} event on {@code kissan.recording.v1}. */
public record RecordingCapturedData(
        UUID recordingId,
        UUID contributorId,
        UUID questionId,
        String category,
        String mediaKey,
        Integer durationMs) {
}
