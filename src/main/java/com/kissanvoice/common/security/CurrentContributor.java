package com.kissanvoice.common.security;

import com.kissanvoice.common.error.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the caller from the bearer token and refuses cross-contributor access.
 *
 * The prototype took the username straight from the URL path, so anyone could
 * read or delete anyone else's recordings by editing the address bar.
 */
@Component
public class CurrentContributor {

    public UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ForbiddenException("No authenticated contributor on this request.");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException("Token subject is not a contributor id.");
        }
    }

    /** Verifies the path id matches the token subject and returns it. */
    public UUID requireSelf(UUID pathId) {
        UUID self = id();
        if (!self.equals(pathId)) {
            throw new ForbiddenException("Token does not grant access to contributor " + pathId);
        }
        return self;
    }
}
