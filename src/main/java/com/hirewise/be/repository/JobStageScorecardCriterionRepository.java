package com.hirewise.be.repository;

import com.hirewise.be.domain.JobStageScorecardCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link JobStageScorecardCriterion} entities (UC-27/28).
 */
public interface JobStageScorecardCriterionRepository extends JpaRepository<JobStageScorecardCriterion, UUID> {

    List<JobStageScorecardCriterion> findByJobStageScorecard_IdOrderByPositionAsc(UUID jobStageScorecardId);

    void deleteByJobStageScorecard_Id(UUID jobStageScorecardId);
}
