package com.hirewise.be.repository;

import com.hirewise.be.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
