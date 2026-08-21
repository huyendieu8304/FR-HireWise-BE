package com.hirewise.be.domain;

/**
 * How a user authenticates.
 * LOCAL (email/password) and GOOGLE (SSO) to coexist for the same {@link User} - see {@link AuthIdentity}.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
