package com.hirewise.be.repository;

import com.hirewise.be.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link Application} entities.
 * <p>
 * Only the one query method UC-06 (Delete Stage) needs exists here for
 * now - the full Application feature (UC-17 and later) is out of scope
 * of Pipeline Configuration and will add its own methods when built.
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
}
