package com.kissanvoice.outbox.events;

import java.util.UUID;

/** Payload {@code data} for the {@code ContributorMilestoneReached} event on {@code kissan.milestone.v1}. */
public record ContributorMilestoneReachedData(
        UUID contributorId,
        long recordingCount,
        int threshold) {
}
