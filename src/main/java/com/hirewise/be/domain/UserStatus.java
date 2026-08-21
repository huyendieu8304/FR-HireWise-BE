package com.hirewise.be.domain;

/**
 * Internal account status.
 * <ul>
 *   <li>{@code INVITED} - account created by an HR Admin (UC-02) but the
 *       user has not yet set a password via the EM-01 activation link.
 *       Cannot log in until activated.</li>
 *   <li>{@code ACTIVE} - normal, usable account.</li>
 *   <li>{@code BLOCKED} / {@code DISABLED} - must be rejected immediately at
 *       RBAC layer 1 (Authentication Freshness - BR-AUTH-07), regardless of
 *       whether the access token is still valid.</li>
 * </ul>
 */
public enum UserStatus {
    INVITED,
    ACTIVE,
    BLOCKED,
    DISABLED
}
