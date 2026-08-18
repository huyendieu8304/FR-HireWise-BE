package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a job posting created by a recruiter. Used as the sample
 * entity demonstrating the full stack: controller (RBAC via Keycloak
 * roles) -> service -> repository -> Postgres (Supabase), with schema
 * managed by Flyway (see
 * src/main/resources/db/migration/__create_job_postings_table.sql).
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

    /** Subject (the "sub" claim - Keycloak user id) of whoever created this job posting. */
    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    /**
     * Recruiter assigned as owner of this job posting - the owner field for
     * RBAC layer 4 (Ownership): only this recruiter can edit the job
     * posting via JOB_EDIT, see {@code JobPostingOwnershipResolver}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id")
    private User recruiter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
