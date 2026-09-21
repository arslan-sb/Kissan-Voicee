package com.kissanvoice.contributor.api.dto;

import com.kissanvoice.contributor.domain.Contributor;

import java.util.UUID;

/**
 * The minimal, non-sensitive public profile used by automation (Block 6's
 * n8n "enrich" step) - no phone number, unlike {@link ContributorResponse}.
 * See SecurityConfig for why this endpoint is permitAll while the full
 * contributor GET stays behind the self-only bearer check.
 */
public record ContributorSummaryResponse(
        UUID id,
        String displayName,
        String locale
) {
    public static ContributorSummaryResponse from(Contributor c) {
        return new ContributorSummaryResponse(c.getId(), c.getDisplayName(), c.getLocale());
    }
}
