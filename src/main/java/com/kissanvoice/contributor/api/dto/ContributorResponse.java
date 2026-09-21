package com.kissanvoice.contributor.api.dto;

import com.kissanvoice.contributor.domain.Contributor;

import java.time.Instant;
import java.util.UUID;

public record ContributorResponse(
        UUID id,
        String displayName,
        String phone,
        String locale,
        String status,
        Instant createdAt
) {
    public static ContributorResponse from(Contributor c) {
        return new ContributorResponse(c.getId(), c.getDisplayName(), c.getPhone(),
                c.getLocale(), c.getStatus().name(), c.getCreatedAt());
    }
}
