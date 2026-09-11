package com.hirewise.be.repository;

import com.hirewise.be.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Application} entities.
 */
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    /**
     * BR-PIPE-03: how many applications currently have {@code current_stage_id}
     * pointing at this stage - a Stage can only be deleted/deactivated
     * when this is zero.
     *
     * @param stageId id of the pipeline stage
     * @return number of applications currently at this stage
     */
    long countByCurrentStage_Id(Long stageId);

    /**
     * BR-APPLY-02: at most one Application per (candidate, job) pair - used
     * by UC-17 to detect a repeat application (AF-01) and update the
     * existing row/CV instead of inserting a duplicate.
     *
     * @param candidateId    candidate id
     * @param jobPositionId  job position id
     * @return the existing application for this pair, if one exists
     */
    Optional<Application> findByCandidate_IdAndJobPosition_Id(UUID candidateId, UUID jobPositionId);

    /**
     * UC-22: every Application currently on a Job's Kanban board, with its
     * {@link com.hirewise.be.domain.Candidate} and {@link com.hirewise.be.domain.PipelineStage}
     * eagerly fetched in the same query - avoids N+1 when the caller renders
     * one card per Application, grouped by {@code currentStage}.
     *
     * @param jobPositionId id of the job position
     * @return every application for this job, oldest stage-change first
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.candidate
            JOIN FETCH a.currentStage
            WHERE a.jobPosition.id = :jobPositionId
            ORDER BY a.lastStageChangedAt ASC
            """)
    List<Application> findByJobPosition_IdFetchCandidateAndStage(@Param("jobPositionId") UUID jobPositionId);

    /**
     * "Quét cả cột" (bulk AI Screening button trên Kanban): mọi Application
     * của 1 Job đang nằm ở đúng 1 Stage (cột) cụ thể - oldest applied first,
     * để phân tích những hồ sơ chờ lâu nhất trước.
     *
     * @param jobPositionId id of the job position
     * @param stageId       id of the pipeline stage (column)
     * @return every application of this job currently sitting in this stage
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.candidate
            WHERE a.jobPosition.id = :jobPositionId AND a.currentStage.id = :stageId
            ORDER BY a.appliedAt ASC
            """)
    List<Application> findByJobPosition_IdAndCurrentStage_Id(
            @Param("jobPositionId") UUID jobPositionId, @Param("stageId") Long stageId);

    /**
     * UC-32: how many candidates each sharing channel actually brought in for
     * one Job, grouped by the {@code utm_source} captured at apply time.
     *
     * <p>Rows with a {@code null} source (candidates who reached the Job Board
     * directly) are excluded here - the stats panel reports them separately as
     * a total rather than as a channel row.</p>
     *
     * @param jobPositionId id of the job position
     * @return {@code [source, count]} pairs, one per distinct non-null source
     */
    @Query("""
            SELECT a.source, COUNT(a) FROM Application a
            WHERE a.jobPosition.id = :jobPositionId AND a.source IS NOT NULL
            GROUP BY a.source
            """)
    List<Object[]> countByJobGroupedBySource(@Param("jobPositionId") UUID jobPositionId);

    /**
     * UC-32: total applications for the Job, the denominator the per-channel
     * counts are compared against.
     *
     * @param jobPositionId id of the job position
     * @return number of applications for this job, whatever their source
     */
    long countByJobPosition_Id(UUID jobPositionId);

    /**
     * UC-41 (SLA Monitoring): every Application currently sitting in a
     * non-terminal Stage that has an SLA threshold configured - the
     * candidate set {@code SlaMonitoringService} then narrows down to actual
     * breaches by comparing {@code lastStageChangedAt} against
     * {@code currentStage.slaHours} in Java (a plain hour-count, not worth a
     * database-specific interval expression). Deliberately NOT filtered on
     * {@code slaAlertSentAt} here - both the read-only alert list (always
     * shows every CURRENT breach) and {@code SlaBreachWorker} (only emails
     * the unalerted ones) start from this same candidate set.
     *
     * @return candidates for SLA breach evaluation, with candidate/stage/job/
     *         hiring-manager eagerly fetched to avoid N+1 while grouping
     */
    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.candidate
            JOIN FETCH a.currentStage s
            JOIN FETCH a.jobPosition j
            LEFT JOIN FETCH j.hiringManager
            WHERE s.slaHours IS NOT NULL AND s.terminal = false
            ORDER BY a.lastStageChangedAt ASC
            """)
    List<Application> findSlaBreachCandidates();
}
