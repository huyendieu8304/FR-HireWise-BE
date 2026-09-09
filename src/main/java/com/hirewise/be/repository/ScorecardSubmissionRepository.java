package com.hirewise.be.repository;

import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.domain.ScorecardSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ScorecardSubmission} entities (UC-28).
 */
public interface ScorecardSubmissionRepository extends JpaRepository<ScorecardSubmission, UUID> {

    /** UC-28 step 1: the current user's own (draft-or-submitted) submission for 1 Interview. */
    Optional<ScorecardSubmission> findByInterview_IdAndEvaluator_Id(UUID interviewId, Long evaluatorId);

    /** Read-only "who has scored this Interview so far" view (every evaluator, not just the caller). */
    @Query("""
            SELECT s FROM ScorecardSubmission s
            JOIN FETCH s.evaluator
            WHERE s.interview.id = :interviewId
            """)
    List<ScorecardSubmission> findByInterview_IdFetchEvaluator(@Param("interviewId") UUID interviewId);

    /**
     * Aggregate view for the Applicant Card [Scorecard] tab - every
     * submission across every Interview of 1 Application.
     */
    @Query("""
            SELECT s FROM ScorecardSubmission s
            JOIN FETCH s.evaluator
            JOIN FETCH s.interview i
            WHERE i.application.id = :applicationId
            ORDER BY i.interviewDate DESC, i.interviewTime DESC
            """)
    List<ScorecardSubmission> findByInterview_Application_IdFetchDetails(@Param("applicationId") UUID applicationId);

    /** AF-01: whether a (Job, Stage) Scorecard already has real grading history - if so, editing it must version instead of overwrite. */
    boolean existsByJobStageScorecard_Id(UUID jobStageScorecardId);

    /**
     * Candidate rows for {@code ScorecardLockWorker} (BR-SCORE-03) - every
     * still-unlocked submission whose Interview started on or before today
     * (UTC-naive, same convention as {@code InterviewService}). The worker
     * does the precise "> 24h ago" check in Java once the Interview is
     * fetched, since combining 2 columns (date + time) into a single
     * comparable instant isn't expressible directly in JPQL here.
     */
    @Query("""
            SELECT s FROM ScorecardSubmission s
            JOIN FETCH s.interview i
            WHERE s.lockedAt IS NULL AND i.interviewDate <= :today
            """)
    List<ScorecardSubmission> findUnlockedWithInterviewOnOrBeforeDate(@Param("today") LocalDate today);

    long countByInterview_Application_IdAndStatus(UUID applicationId, ScorecardSubmissionStatus status);
}
