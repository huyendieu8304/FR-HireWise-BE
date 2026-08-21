package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * This is the session/token registry that lets RBAC layer 1 revoke
 * a still-unexpired access token immediately (BR-AUTH-04, BR-AUTH-07) - see {@code security.SessionRegistryService}.
 * <p>
 * The access token embeds this row's id as the {@code sid} claim
 * (see {@code security.token.JwtTokenService}); {@code refreshTokenHash} lets
 * {@code POST /api/auth/refresh} mint a new access token without asking
 * for credentials again, for up to {@code expiresAt} (BR-AUTH-03: 7 days).
 */
@Entity
@Table(name = "user_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Argon2id hash of the opaque refresh token handed to the client - never store it raw. */
    @Column(name = "refresh_token_hash", nullable = false, length = 255)
    private String refreshTokenHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    /** refresh token expiry (7 days from login/last refresh). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set on logout, or when an HR Admin blocks the account (BR-AUTH-04). Null while active. */
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
