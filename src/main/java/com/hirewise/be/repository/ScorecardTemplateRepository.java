package com.hirewise.be.repository;

import com.hirewise.be.domain.ScorecardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ScorecardTemplate} entities (UC-27) - HR Admin's
 * Master Template library, always company-wide (no Job/Stage scoping, so no
 * job-scoped lookup methods here - see {@code JobStageScorecardRepository}
 * for the real per-(Job, Stage) scoring definition's queries).
 */
public interface ScorecardTemplateRepository extends JpaRepository<ScorecardTemplate, UUID> {

    List<ScorecardTemplate> findAllByOrderByCreatedAtDesc();
}
