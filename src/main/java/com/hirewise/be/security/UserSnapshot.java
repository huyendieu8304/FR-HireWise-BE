package com.hirewise.be.security;

import com.hirewise.be.domain.UserStatus;

/**
 * Compact snapshot of the internal {@code users} record, loaded once per
 * request by RBAC layer 1 (Authentication Freshness, short-TTL cache - see
 * {@link UserDirectoryService}) so {@link AuthenticationFreshnessFilter}
 * can decide whether to let the request through, and later read back by
 * {@link CurrentUserResolver} to populate the internal userId on
 * {@link CurrentUser}.
 * <p>
 * - {@code status}: needed by RBAC layer 1 (BR-AUTH-07) to know whether the
 *   account is still ACTIVE at request time, not just at the time the JWT
 *   was issued.
 * - {@code userId}: internal id, used as the owner id for RBAC layer 4
 *   (Ownership) and for services to query data related to this user.
 */
public record UserSnapshot(Long userId, UserStatus status) {
    /** Request attribute key used to store the snapshot on the
     * {@code HttpServletRequest} - see {@link AuthenticationFreshnessFilter}
     * and {@link CurrentUserResolver}. */
    public static final String REQUEST_ATTRIBUTE = "hirewise.userSnapshot";
}
