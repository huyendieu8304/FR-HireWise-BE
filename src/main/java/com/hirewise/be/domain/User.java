package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ban sao noi bo cua danh tinh Keycloak (keycloakId = claim "sub"). Can
 * thiet de RBAC layer 1 (Authentication Freshness, BR-AUTH-07) kiem tra
 * status=ACTIVE tai thoi diem xu ly request, va lam goc cho user_roles /
 * user_access_scopes (JWT khong the mang du lieu nay vi no thay doi ngoai
 * vong doi cua 1 access token).
 *
 * department o day CHI la phong ban to chuc CHINH (BR-RBAC-05), KHONG phai
 * pham vi truy cap du lieu - xem UserAccessScope.
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
