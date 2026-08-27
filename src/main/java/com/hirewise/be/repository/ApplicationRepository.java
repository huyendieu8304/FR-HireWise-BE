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
}
