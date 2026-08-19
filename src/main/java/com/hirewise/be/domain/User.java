package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An internal HireWise account (HR Admin, Recruiter, Hiring Manager,
 * Interviewer). Accounts are managed entirely by this application in the
 * SAME business database - there is no external Identity Provider anymore.
 * A user's actual credentials (password hash for LOCAL login, or the
 * linked Google subject for SSO) live in {@link AuthIdentity}, not here -
 * one user can have more than one login method (UC-01 AF-01).
 * <p>
 * {@code id} (the internal {@code user_id}) is the identity carried in our
 * own access tokens ("sub" claim, see {@code security.token.JwtTokenService}) and
 * is what RBAC layer 1 (Authentication Freshness, BR-AUTH-07) re-checks
 * {@code status=ACTIVE} against on every request, via
 * {@code security.UserDirectoryService}.
 * <p>
 * {@code department} here is ONLY the user's primary organizational
 * department (BR-RBAC-05), NOT their data access scope - see
 * {@link UserAccessScope}.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** set on every successful login. */
    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
