package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Encrypted OAuth token pair for one {@link IntegrationConnection} (UC-07).
 * BR-STORAGE-01: access/refresh tokens must be encrypted at rest, never
 * returned by any read API. A Reconnect (UC-08 AF-01) replaces this row's
 * values in place rather than inserting a new one.
 */
@Entity
@Table(name = "oauth_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OauthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oauth_token_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_connection_id", nullable = false, unique = true)
    private IntegrationConnection integrationConnection;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "text")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "text")
    private String refreshTokenEncrypted;

    @Column(name = "token_type", nullable = false, length = 20)
    private String tokenType;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
