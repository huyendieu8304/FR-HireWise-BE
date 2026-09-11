package com.hirewise.be.repository;

import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Interview} entities (UC-24).
 */
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findAllByApplication_IdAndStatus(UUID applicationId, InterviewStatus status);

    @Query("""
            SELECT i FROM Interview i
            JOIN FETCH i.scheduledBy
            WHERE i.application.id = :applicationId
            ORDER BY i.interviewDate DESC, i.interviewTime DESC
            """)
    List<Interview> findByApplication_IdFetchDetails(@Param("applicationId") UUID applicationId);

    Optional<Interview> findFirstByApplication_IdOrderByCreatedAtDesc(UUID applicationId);

    @Query("""
            SELECT DISTINCT i FROM Interview i
            JOIN FETCH i.application a
            JOIN FETCH a.candidate c
            JOIN FETCH a.jobPosition j
            LEFT JOIN FETCH j.recruiter
            LEFT JOIN FETCH j.hiringManager
            LEFT JOIN FETCH i.scheduledBy
            LEFT JOIN FETCH i.participants p
            LEFT JOIN FETCH p.interviewer
            WHERE i.interviewDate BETWEEN :startDate AND :endDate
                AND i.status != 'CANCELLED'
            ORDER BY i.interviewDate ASC, i.interviewTime ASC
            """)
    List<Interview> findBetweenDates(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);
}
