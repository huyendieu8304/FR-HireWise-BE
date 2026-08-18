package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quyen nguyen tu (RESOURCE_ACTION, vd JOB_EDIT). isWrite phan biet hanh
 * dong doc/ghi de RBAC layer 3 (Access Scope) biet can can_write=true hay
 * chi can_write=false tren access scope lien quan.
 */
@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(name = "is_write", nullable = false)
    private boolean write;
}
