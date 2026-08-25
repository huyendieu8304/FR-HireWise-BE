package com.hirewise.be.repository;

import com.hirewise.be.domain.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link PipelineStage} entities.
 */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    /**
     * UC-04 step 1: current stages of a template, in Kanban column order.
     *
     * @param templateId id of the parent pipeline template
     * @return stages ordered by {@code position} ascending
     */
    List<PipelineStage> findByPipelineTemplate_IdOrderByPositionAsc(Long templateId);

    /**
     * UC-04/UC-05 step 1: stages actually shown/reorderable on the Kanban
     * board - excludes stages soft-deleted by UC-06 ({@code is_active = false}),
     * which are kept in the database (for {@code application_stage_history}
     * integrity) but must never resurface in the UI or be reorderable.
     *
     * @param templateId id of the parent pipeline template
     * @return active stages ordered by {@code position} ascending
     */
    List<PipelineStage> findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(Long templateId);

    /**
     * BR-PIPE-02: whether {@code code} is already used by another stage in
     * the same template - checked before insert so a duplicate is reported
     * as a clean 409 (EX-01) rather than a raw unique-constraint violation.
     *
     * @param templateId id of the parent pipeline template
     * @param code       candidate stage code
     * @return {@code true} if a stage with this code already exists in the template
     */
    boolean existsByPipelineTemplate_IdAndCode(Long templateId, String code);

    /**
     * BR-PIPE-04: the current highest {@code position} in the template, used
     * to compute the next position for a newly-appended stage.
     *
     * @param templateId id of the parent pipeline template
     * @return the current maximum position in the template, or 0 if it has no stages yet
     */
    @Query("SELECT COALESCE(MAX(s.position), 0) FROM PipelineStage s WHERE s.pipelineTemplate.id = :templateId")
    int findMaxPosition(@Param("templateId") Long templateId);
}
