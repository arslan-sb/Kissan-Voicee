package com.kissanvoice.outbox.events;

import java.util.UUID;

/** Payload {@code data} for the {@code RecordingDeleted} event on {@code kissan.recording.v1}. */
public record RecordingDeletedData(
        UUID recordingId,
        UUID contributorId,
        UUID questionId) {
}
