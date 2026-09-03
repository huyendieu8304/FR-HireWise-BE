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

    void deleteByInterview_Id(UUID interviewId);
}
