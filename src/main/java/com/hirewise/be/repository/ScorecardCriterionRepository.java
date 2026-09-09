package com.hirewise.be.repository;

import com.hirewise.be.domain.ScorecardCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ScorecardCriterion} entities (UC-27).
 */
public interface ScorecardCriterionRepository extends JpaRepository<ScorecardCriterion, UUID> {

    List<ScorecardCriterion> findByScorecardTemplate_IdOrderByPositionAsc(UUID scorecardTemplateId);

    void deleteByScorecardTemplate_Id(UUID scorecardTemplateId);
}
