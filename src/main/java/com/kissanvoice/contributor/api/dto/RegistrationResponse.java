package com.kissanvoice.contributor.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record RegistrationResponse(
        ContributorResponse contributor,
        @Schema(description = "Paste into Swagger's Authorize dialog") String accessToken,
        String tokenType,
        Instant expiresAt
) {}
