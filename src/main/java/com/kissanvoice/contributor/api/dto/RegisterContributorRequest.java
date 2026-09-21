package com.kissanvoice.contributor.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterContributorRequest(

        @Schema(example = "Arslan Shaukat")
        @NotBlank(message = "displayName is required")
        @Size(max = 120, message = "displayName must be at most 120 characters")
        String displayName,

        @Schema(example = "+923001234567", description = "Optional. Must be unique across contributors.")
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,31}$", message = "phone is not a valid number")
        String phone,

        @Schema(example = "ur-PK", defaultValue = "ur-PK")
        @Size(max = 16)
        String locale
) {}
