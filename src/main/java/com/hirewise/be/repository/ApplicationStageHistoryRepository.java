package com.hirewise.be.repository;

import com.hirewise.be.domain.ApplicationStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ApplicationStageHistory} entities - the immutable
 * stage-change log. UC-17 only ever appends the very first ("New") event
 * here; reading it back belongs to later use cases (UC-20 Applicant Card
 * timeline).
 */
public interface ApplicationStageHistoryRepository extends JpaRepository<ApplicationStageHistory, Long> {

    /**
     * UC-20: the full stage-change timeline for one Application, oldest
     * first, with {@code fromStage}/{@code toStage}/{@code changedBy}
     * eagerly fetched to avoid N+1 when rendering the Applicant Card.
     *
     * @param applicationId application id
     * @return every stage-change event for this application, chronological order
     */
    @Query("""
            SELECT h FROM ApplicationStageHistory h
            LEFT JOIN FETCH h.fromStage
            JOIN FETCH h.toStage
            LEFT JOIN FETCH h.changedBy
            WHERE h.application.id = :applicationId
            ORDER BY h.changedAt ASC
            """)
    List<ApplicationStageHistory> findByApplication_IdOrderByChangedAtAsc(@Param("applicationId") UUID applicationId);
}
