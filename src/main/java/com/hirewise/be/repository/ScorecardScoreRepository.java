package com.hirewise.be.repository;

import com.hirewise.be.domain.ScorecardScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ScorecardScore} entities (UC-28).
 */
public interface ScorecardScoreRepository extends JpaRepository<ScorecardScore, UUID> {

    List<ScorecardScore> findBySubmission_Id(UUID submissionId);

    List<ScorecardScore> findBySubmission_IdIn(List<UUID> submissionIds);
}
