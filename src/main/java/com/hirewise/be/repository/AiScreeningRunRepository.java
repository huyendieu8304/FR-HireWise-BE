package com.hirewise.be.repository;

import com.hirewise.be.domain.AiScreeningRun;
import com.hirewise.be.domain.AiScreeningStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for {@link AiScreeningRun} (UC-21) - see {@code event.AiScreeningDispatcher}. */
public interface AiScreeningRunRepository extends JpaRepository<AiScreeningRun, Long> {

    /** UC-21 main flow: the run currently shown on the Applicant Card / Kanban Badge. */
    Optional<AiScreeningRun> findFirstByApplication_IdOrderByCreatedAtDesc(UUID applicationId);

    /** {@code event.AiScreeningDispatcher}'s poll query - oldest-first, same pattern as {@code OutboxEventRepository}. */
    @Query("SELECT r FROM AiScreeningRun r WHERE r.status = :status ORDER BY r.createdAt ASC")
    List<AiScreeningRun> findBatchByStatus(AiScreeningStatus status, Pageable pageable);
}
