package com.hirewise.be.domain;

/**
 * Internal account status. BLOCKED/DISABLED must be rejected immediately
 * at RBAC layer 1 (Authentication Freshness - BR-AUTH-07), regardless of
 * whether the JWT is still valid.
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DISABLED
}
