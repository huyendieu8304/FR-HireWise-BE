package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * RBAC layer 3 (Access Scope). A user can have MULTIPLE rows with
 * {@code scope_type=DEPARTMENT} pointing at different departments at the
 * same time (BR-RBAC-05 - they're UNIONed when checking permissions).
 * {@code includeSubDepartments=true} (the default) means the scope is
 * also inherited down to descendant departments (BR-RBAC-06).
 */
@Entity
@Table(name = "user_access_scopes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccessScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scope_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private ScopeType scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * For {@code scope_type=JOB}: the raw id of the job_postings row. Not
     * mapped as a JPA relationship, to avoid a two-way dependency cycle
     * between the two entities just for this one scope field.
     */
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "include_sub_departments", nullable = false)
    private boolean includeSubDepartments;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;
}
