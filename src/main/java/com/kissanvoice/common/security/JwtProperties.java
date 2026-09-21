package com.kissanvoice.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret    HMAC signing key. Must be at least 32 bytes for HS256.
 *                  Injected from the environment in anything but local dev.
 * @param ttlHours  token lifetime
 * @param issuer    iss claim
 */
@ConfigurationProperties(prefix = "kissanvoice.jwt")
public record JwtProperties(String secret, long ttlHours, String issuer) {

    public JwtProperties {
        if (secret == null || secret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "kissanvoice.jwt.secret must be at least 32 bytes for HS256");
        }
        if (ttlHours <= 0) {
            ttlHours = 24;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "kissan-voice-api";
        }
    }
}
