package com.kissanvoice.outbox.events;

import java.util.UUID;

/** Payload {@code data} for the {@code ContributorRegistered} event on {@code kissan.contributor.v1}. */
public record ContributorRegisteredData(
        UUID contributorId,
        String displayName,
        String phone,
        String locale) {
}
