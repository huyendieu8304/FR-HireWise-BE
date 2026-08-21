package com.hirewise.be.security;

import com.hirewise.be.domain.UserStatus;

/**
 * Compact snapshot of the internal {@code users} record, loaded once per
 * request by RBAC layer 1 (Authentication Freshness, short-TTL cache - see
 * {@link UserDirectoryService}) so {@link AuthenticationFreshnessFilter}
 * can decide whether to let the request through.
 */
public record UserSnapshot(Long userId, UserStatus status) {
}
