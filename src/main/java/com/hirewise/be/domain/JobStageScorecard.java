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
 * The REAL Scorecard scoring definition (UC-27/UC-28) - scoped to exactly 1
 * ({@link #job}, {@link #pipelineStage}) pair. A {@link PipelineTemplate}
 * can be reused by many Jobs, and 1 Job's pipeline can have several
 * {@code INTERVIEW}-type Stages (e.g. "Phone Screen", "Technical
 * Interview", "Final Interview") - each needs completely independent
 * criteria, so Scorecard cannot be attached to the Stage alone (that would
 * collide across every Job reusing the same Pipeline Template) nor to the
 * Job alone (that would collide across every Interview-type Stage of that
 * same Job's pipeline). The pair is the actual unit of configuration.
 * <p>
 * May optionally trace back to the {@link ScorecardTemplate} it was cloned
 * from ({@link #sourceMasterTemplate}) purely for "created from" display -
 * never read back for behavior, so editing/archiving that Master Template
 * afterwards never affects this row (Hard requirement from the team: "sua
 * Master Template goc sau nay khong anh huong nguoc").
 * <p>
 * AF-01: editing a version already referenced by >=1
 * {@link ScorecardSubmission} creates a NEW row ({@code version + 1})
 * instead of mutating this one in place - see
 * {@code JobStageScorecardService#update} - so a submission always keeps
 * scoring against the exact criteria/weights that existed when it was
 * graded.
 */
@Entity
@Table(name = "job_stage_scorecards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStageScorecard {

    @Id
    @Column(name = "job_stage_scorecard_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JobPosition job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_stage_id", nullable = false)
    private PipelineStage pipelineStage;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScorecardStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_master_template_id")
    private ScorecardTemplate sourceMasterTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "jobStageScorecard", fetch = FetchType.LAZY)
    private List<JobStageScorecardCriterion> criteria = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
