package com.hirewise.be.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One {@link JobStageScorecardCriterion} rating within a
 * {@link ScorecardSubmission} (UC-28) - the star/scale value plus its
 * required qualitative comment (BR-SCORE-01). {@code score}/{@code comment}
 * start {@code null} and are filled in progressively while the submission
 * is still {@code DRAFT}.
 */
@Entity
@Table(name = "scorecard_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardScore {

    @Id
    @Column(name = "score_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ScorecardSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private JobStageScorecardCriterion criterion;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
