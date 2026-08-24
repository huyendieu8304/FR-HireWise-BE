package com.hirewise.be.repository;

import com.hirewise.be.domain.PipelineTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for {@link PipelineTemplate} entities.
 */
public interface PipelineTemplateRepository extends JpaRepository<PipelineTemplate, Long> {

    /**
     * UC-04 step 1: templates an HR Admin can choose from when opening
     * Pipeline Management.
     *
     * @return every pipeline template, most recently created first
     */
    List<PipelineTemplate> findAllByOrderByCreatedAtDesc();
}
