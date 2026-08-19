package com.hirewise.be.security;

import java.util.Set;

/**
 * Compact view of the current user - contains ONLY the fields actually
 * consumed elsewhere in the code (controllers, services, RBAC layers 2-4).
 * <p>
 * {@code userId} comes straight from the access token's {@code sub} claim
 * (see {@code security.token.JwtTokenService}) - our own tokens are self-issued after RBAC
 * layer 1 (Authentication Freshness) has already confirmed the account
 * exists and is ACTIVE, so it is always non-null for any request that made
 * it past {@link AuthenticationFreshnessFilter}.
 * <p>
 * {@code roles} is deliberately NOT read from the token - {@link CurrentUserResolver}
 * resolves it fresh (short-TTL cache, see {@link ActiveRolesService}) on every
 * request, so a role revoked by an HR Admin (UC-03 AF-01) takes effect almost
 * immediately instead of only on the user's next login.
 */
public record CurrentUser(
        Long userId,
        String email,
        String fullName,
        Set<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role.toUpperCase());
    }
}
