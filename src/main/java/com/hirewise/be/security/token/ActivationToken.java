package com.hirewise.be.security.token;

import com.hirewise.be.domain.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time token backing the activation link and, later, a
 * password-reset flow. Only the SHA-256 hash of the raw token is stored -
 * the raw value is emailed to the user once and never persisted, so a DB
 * leak alone can't be used to activate/reset an account.
 */
@Entity
@Table(name = "activation_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationToken {

    @Id
    @Column(name = "token_id")
    private UUID tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivationTokenPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
