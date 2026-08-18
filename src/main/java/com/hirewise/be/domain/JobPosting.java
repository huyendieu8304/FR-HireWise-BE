package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entity mẫu để minh hoạ full luồng: controller (RBAC theo role Keycloak)
 * -> service -> repository -> Postgres (Supabase), migrate bằng Flyway
 * (xem src/main/resources/db/migration/__create_job_postings_table.sql).
 */
@Entity
@Table(name = "job_postings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    /** subject (sub claim - id user Keycloak) của người tạo job posting */
    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    /** Recruiter được gán phụ trách - owner field cho RBAC layer 4
     * (Ownership): chỉ recruiter này mới được sửa job qua JOB_EDIT, xem
     * JobPostingOwnershipResolver. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id")
    private User recruiter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
