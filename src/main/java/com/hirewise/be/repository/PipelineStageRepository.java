package com.hirewise.be.repository;

import com.hirewise.be.domain.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for {@link PipelineStage} entities.
 */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    /**
     * BR-APPLY-04: the stage a brand-new Application is placed into -
     * always the first active stage (lowest {@code position}) of the Job's
     * Pipeline Template.
     *
     * @param pipelineTemplateId the Job's assigned pipeline template
     * @return the first active stage, if the template has one configured
     */
    Optional<PipelineStage> findFirstByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(Long pipelineTemplateId);
}
