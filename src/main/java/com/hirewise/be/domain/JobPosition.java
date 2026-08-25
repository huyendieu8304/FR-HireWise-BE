package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a job position created by a recruiter (UC-12), assigned a
 * {@link PipelineTemplate} and submitted for approval (UC-13/UC-14/UC-15),
 * then published to the public Job Board (UC-16) where candidates apply
 * (UC-17).
 */
@Entity
@Table(name = "job_positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosition {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    /** Job description block  */
    @Column(columnDefinition = "text")
    private String description;

    /** Candidate requirements block ("Yeu cau ung vien") of the 3-block JD (UC-12). */
    @Column(columnDefinition = "text")
    private String requirements;

    /** Benefits block */
    @Column(columnDefinition = "text")
    private String benefits;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Work location shown on the public Job Board (UC-16 normal flow step 2), e.g. "Ho Chi Minh". */
    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @Column(name = "salary_min", precision = 14, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 14, scale = 2)
    private BigDecimal salaryMax;

    /** BR-JOB-01: required, must be >= 1. */
    @Column(nullable = false)
    private int openings;

    /** Optional; BR-JOB-03: must be a future date when set. */
    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /**
     * Recruiter assigned as owner of this job position - the owner field for
     * RBAC layer 4 (Ownership): only this recruiter can edit the job
     * position via JOB_EDIT, see {@code JobPositionOwnershipResolver}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id")
    private User recruiter;

    /**
     * Hiring Manager who reviews and Approves/Rejects this Job Position
     * (UC-14/UC-15, BR-APR-01). The actual decision trail lives in
     * {@link JobApproval}, not here.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hiring_manager_id")
    private User hiringManager;

    /**
     * Pipeline the Job's applications flow through, assigned when the
     * Recruiter submits for approval (UC-13). {@code null} while still Draft.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_template_id")
    private PipelineTemplate pipelineTemplate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
