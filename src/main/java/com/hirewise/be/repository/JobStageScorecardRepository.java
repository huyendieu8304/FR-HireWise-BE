package com.hirewise.be.repository;

import com.hirewise.be.domain.JobStageScorecard;
import com.hirewise.be.domain.ScorecardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link JobStageScorecard} entities - the real per-(Job,
 * Stage) Scorecard scoring definition (UC-27/28).
 */
public interface JobStageScorecardRepository extends JpaRepository<JobStageScorecard, UUID> {

    /**
     * The current version of the Scorecard configured for exactly this
     * (Job, Stage) pair - used both to resolve which Scorecard an evaluator
     * scores against (UC-28) and to check the UC-14 Approve gate (UC-15
     * cannot approve until every {@code INTERVIEW}-type Stage of the Job's
     * pipeline has one).
     */
    Optional<JobStageScorecard> findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
            UUID jobId, Long pipelineStageId, ScorecardStatus status);

    /**
     * Every currently-{@code ACTIVE} Scorecard configured for a Job (1 per
     * Interview-type Stage that already has one) - used to build the
     * per-stage "configured / missing" checklist on the Job Approval detail
     * screen in a single query instead of 1 query per Stage.
     */
    List<JobStageScorecard> findByJob_IdAndStatus(UUID jobId, ScorecardStatus status);
}
