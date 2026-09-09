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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HR Admin's MASTER SCORECARD TEMPLATE LIBRARY (UC-27) - a named, reusable
 * set of sample criteria (e.g. "Technical - Backend", "Culture Fit -
 * Chung"), always company-wide, never scoped to a Job or Stage. Purely
 * reference/clone material: nothing ever scores against a
 * {@code ScorecardTemplate} directly - see {@link JobStageScorecard} for
 * the real per-(Job, Stage) scoring definition that Hiring Managers build by
 * either cloning one of these as a starting point or starting from scratch.
 * <p>
 * Because nothing depends on this row for reproducibility of past scores
 * (that guarantee lives entirely in {@link JobStageScorecard}'s own AF-01
 * versioning instead), a Master Template is always edited in place - no
 * versioning here, just a simple {@code ACTIVE}/{@code ARCHIVED} status to
 * let HR Admin soft-hide retired templates from the picker.
 */
@Entity
@Table(name = "scorecard_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardTemplate {

    @Id
    @Column(name = "scorecard_template_id")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScorecardStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "scorecardTemplate", fetch = FetchType.LAZY)
    private List<ScorecardCriterion> criteria = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
