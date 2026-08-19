package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One login method belonging to a {@link User}. A user may have several
 * (e.g. LOCAL + GOOGLE at once)
 * <p>
 * For {@code provider=LOCAL}: {@code providerSubject} is the login email
 * (kept in sync with {@code users.email}) and {@code passwordHash} holds
 * the Argon2id hash (BR-AUTH-01). {@code failedLoginAttempts}/
 * {@code lockedUntil} implement the brute-force lockout in BR-AUTH-02.
 * <p>
 * For {@code provider=GOOGLE}: {@code providerSubject} is the Google
 * account's stable "sub" claim and {@code passwordHash} is unused (null).
 */
@Entity
@Table(name = "auth_identities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_identity_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** LOCAL: login email. GOOGLE: the Google "sub" claim. */
    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    /** Argon2id hash - only set for {@code provider=LOCAL}. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** BR-AUTH-02: consecutive failed LOCAL login attempts since the last success/reset. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** BR-AUTH-02: set for 15 minutes after the 5th consecutive failure; null when not locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
