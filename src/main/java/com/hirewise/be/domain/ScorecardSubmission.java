package com.hirewise.be.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One evaluator's rating of one {@link Interview} (UC-28) against a
 * {@link JobStageScorecard} snapshot ({@link #jobStageScorecard} is fixed at
 * creation time - never re-pointed even if the (Job, Stage) Scorecard is
 * later edited/versioned, per AF-01/{@code JobStageScorecard}'s own
 * Javadoc).
 * <p>
 * Ownership (RBAC Layer 4, {@code OwnershipPolicyRegistry}): {@link #evaluator}
 * is the sole owner - see {@code ScorecardSubmissionOwnershipResolver}.
 */
@Entity
@Table(name = "scorecard_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardSubmission {

    @Id
    @Column(name = "submission_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private User evaluator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_stage_scorecard_id", nullable = false)
    private JobStageScorecard jobStageScorecard;

    @Column(name = "overall_comment", columnDefinition = "text")
    private String overallComment;

    /** BR-SCORE-02: Sum(score x weight) / Sum(weight) - computed on Submit, {@code null} while DRAFT. */
    @Column(name = "weighted_score", precision = 5, scale = 2)
    private BigDecimal weightedScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScorecardSubmissionStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** BR-SCORE-03: non-null once locked (~24h after the interview starts) - see ScorecardLockWorker. */
    @Column(name = "locked_at")
    private Instant lockedAt;

    @Builder.Default
    @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY)
    private List<ScorecardScore> scores = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
