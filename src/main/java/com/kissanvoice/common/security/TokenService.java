package com.kissanvoice.common.security;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Issues the contributor's bearer token.
 *
 * Self-issued HS256 is deliberate MVP scope: it demonstrates stateless auth
 * without standing up an identity provider. ADR / README note the production
 * path is OIDC against Keycloak, at which point this class disappears and only
 * the decoder remains.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public IssuedToken issue(UUID contributorId, String displayName) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.ttlHours(), ChronoUnit.HOURS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(contributorId.toString())
                .claim("name", displayName)
                .claim("scope", "contributor")
                .build();

        String token = encoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new IssuedToken(token, "Bearer", expiry);
    }

    public record IssuedToken(String accessToken, String tokenType, Instant expiresAt) {}
}
