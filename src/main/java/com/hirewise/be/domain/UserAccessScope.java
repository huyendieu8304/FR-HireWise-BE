package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * RBAC layer 3 (Access Scope). 1 user co the co NHIEU dong scope_type=
 * DEPARTMENT tro toi nhieu phong ban khac nhau cung luc (BR-RBAC-05 - UNION
 * khi check quyen). includeSubDepartments=true (mac dinh) nghia la scope ke
 * thua xuong ca phong ban con (BR-RBAC-06).
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

    /** scope_type=JOB: id truc tiep cua job_postings, khong map quan he JPA
     * de tranh phu thuoc vong hai chieu giua 2 entity chi vi 1 truong scope. */
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "include_sub_departments", nullable = false)
    private boolean includeSubDepartments;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;
}
