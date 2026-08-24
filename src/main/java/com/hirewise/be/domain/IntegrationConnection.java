package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata for one 3rd-party OAuth connection (UC-07/UC-08). Generic across
 * providers/purposes so it can be reused by Calendar/Social integrations
 * later (UC-18/UC-19); the encrypted token itself lives in {@link OauthToken},
 * kept separate so ordinary reads of this table never touch a secret.
 */
@Entity
@Table(name = "integration_connections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_connection_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntegrationProvider provider;

    /** e.g. "CLOUD_STORAGE"; kept as free text since UC-18/UC-19 will add purposes later. */
    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(name = "account_label", length = 255)
    private String accountLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
