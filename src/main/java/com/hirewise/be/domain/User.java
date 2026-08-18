package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Internal mirror of a Keycloak identity ({@code keycloakId} = the "sub"
 * claim). Needed so RBAC layer 1 (Authentication Freshness, BR-AUTH-07)
 * can check {@code status=ACTIVE} at request-processing time, and serves
 * as the anchor for {@code user_roles} / {@code user_access_scopes} (a JWT
 * can't carry this data since it can change within the lifetime of an
 * access token).
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

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 255)
    private String keycloakId;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
