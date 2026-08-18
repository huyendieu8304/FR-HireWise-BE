package com.hirewise.be.security;

import java.util.Set;

/**
 * Compact view of the current user - contains ONLY the fields actually
 * consumed elsewhere in the code (controllers, services, RBAC layers 2-4).
 * Built from JWT claims (Keycloak access token) so controllers/services
 * don't have to dig through raw claims every time they need user info.
 * <p>
 * {@code userId} does NOT come from the JWT - it is loaded by
 * {@link AuthenticationFreshnessFilter} (RBAC layer 1) from the internal
 * {@code users} table (via {@link UserDirectoryService}, which caches it),
 * then read back by {@link CurrentUserResolver} from the request attribute
 * ({@link UserSnapshot#REQUEST_ATTRIBUTE}) to populate this field. It is
 * always non-null for any request that made it past layer 1, since that
 * filter already rejects requests for accounts that were never provisioned
 * in the system.
 */
public record CurrentUser(
        String keycloakId,      // "sub" claim - Keycloak's unique identifier for the user
        String username,        // "preferred_username" claim
        String email,            // "email" claim
        String fullName,         // "name" claim
        Set<String> roles,       // roles "unpacked" from realm_access/resource_access, uppercase, no ROLE_ prefix
        Long userId              // internal id (users table) - used as the owner id for RBAC layer 4 (Ownership)
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role.toUpperCase());
    }
}
