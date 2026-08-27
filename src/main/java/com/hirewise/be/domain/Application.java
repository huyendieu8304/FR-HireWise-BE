package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One Candidate's application to one Job Position (UC-17) - the Kanban card
 * itself. At most one row exists per (candidate, job) pair (BR-APPLY-02);
 * {@link #currentStage} is a fast-read cache of the applicant's Kanban
 * column, while {@link ApplicationStageHistory} is the append-only source of
 * truth for every stage change (out of scope through UC-17, whose only write
 * to it is the initial "New" event created alongside this row).
 */
@Entity
@Table(name = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    /** BR-APPLY-04: always the first stage (position = 1) of the Job's Pipeline Template on creation. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_stage_id", nullable = false)
    private PipelineStage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "last_stage_changed_at")
    private Instant lastStageChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
