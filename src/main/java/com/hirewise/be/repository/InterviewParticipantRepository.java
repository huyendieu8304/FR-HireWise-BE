package com.hirewise.be.repository;

import com.hirewise.be.domain.InterviewParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link InterviewParticipant} entities (UC-24).
 */
public interface InterviewParticipantRepository extends JpaRepository<InterviewParticipant, Long> {

    @Query("""
            SELECT ip FROM InterviewParticipant ip
            JOIN FETCH ip.interviewer
            WHERE ip.interview.id = :interviewId
            """)
    List<InterviewParticipant> findByInterview_IdFetchInterviewer(@Param("interviewId") UUID interviewId);

    @Query("""
            SELECT COUNT(ip) > 0 FROM InterviewParticipant ip
            WHERE ip.interviewer.id = :interviewerId
              AND ip.interview.interviewDate = :interviewDate
              AND ip.interview.interviewTime = :interviewTime
              AND ip.interview.status != :status
            """)
    boolean existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
            @Param("interviewerId") Long interviewerId,
            @Param("interviewDate") java.time.LocalDate interviewDate,
            @Param("interviewTime") java.time.LocalTime interviewTime,
            @Param("status") com.hirewise.be.domain.InterviewStatus status);

    void deleteByInterview_Id(UUID interviewId);
}
