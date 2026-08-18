package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An atomic permission (RESOURCE_ACTION, e.g. JOB_EDIT). {@code isWrite}
 * distinguishes read vs. write actions so that RBAC layer 3 (Access Scope)
 * knows whether the related access scope needs can_write=true, or
 * can_write=false is enough.
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
