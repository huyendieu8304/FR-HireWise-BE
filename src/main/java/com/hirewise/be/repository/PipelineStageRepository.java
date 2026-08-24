package com.hirewise.be.repository;

import com.hirewise.be.domain.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * UC-09: fetches {@link PipelineStage} rows for the "gan stage" dropdown
 * on the Email Template form.
 */
public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    /**
     * Returns all active pipeline stages sorted by position ascending, so the
     * dropdown displays them in the same order they appear in the Kanban board.
     */
    List<PipelineStage> findByActiveTrueOrderByPositionAsc();
}