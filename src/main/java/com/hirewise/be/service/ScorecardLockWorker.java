package com.hirewise.be.service;

import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BR-SCORE-03: locks a {@link ScorecardSubmission} (sets {@code locked_at})
 * once its Interview started more than 24h ago, so its scores/comment become
 * read-only (only the HR Admin-only unlock action, {@code ScorecardSubmissionService#unlock},
 * can reopen it). Applies to BOTH {@code DRAFT} and {@code SUBMITTED} rows -
 * BR-SCORE-03's own wording is about "sau thoi diem phong van ket thuc",
 * not about the submission's own status, so a Scorecard an evaluator never
 * got around to submitting is just as much "past the window" as one they did.
 * <p>
 * {@link com.hirewise.be.domain.Interview} has no explicit end-time/duration
 * field, only a start ({@code interviewDate} + {@code interviewTime}) - the
 * 24h window is measured from that start, the same approximation
 * {@code InterviewService} already makes when comparing "now" against an
 * interview's date/time ({@code LocalDateTime.now(clock)}, no timezone
 * conversion), reused here for consistency.
 * <p>
 * Same {@code @Scheduled} shape as {@code OfferExpiryWorker}.
 */
@Slf4j
@Component
public class ScorecardLockWorker {

    private static final int LOCK_AFTER_HOURS = 24;

    private final ScorecardSubmissionRepository scorecardSubmissionRepository;
    private final Clock clock;

    public ScorecardLockWorker(ScorecardSubmissionRepository scorecardSubmissionRepository, Clock clock) {
        this.scorecardSubmissionRepository = scorecardSubmissionRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.scorecard.lock-poll-interval-ms:300000}")
    @Transactional
    public void lockOverdueSubmissions() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // Narrow candidates in SQL to "Interview started on or before today" (cheap, sargable),
        // then do the precise ">24h ago" check in Java below where date+time can be combined.
        List<ScorecardSubmission> candidates = scorecardSubmissionRepository.findUnlockedWithInterviewOnOrBeforeDate(today);
        if (candidates.isEmpty()) {
            return;
        }

        Instant nowInstant = Instant.now(clock);
        int lockedCount = 0;
        for (ScorecardSubmission submission : candidates) {
            LocalDateTime interviewStart = LocalDateTime.of(
                    submission.getInterview().getInterviewDate(), submission.getInterview().getInterviewTime());
            if (interviewStart.plusHours(LOCK_AFTER_HOURS).isAfter(now)) {
                continue; // not yet past the 24h window
            }
            submission.setLockedAt(nowInstant);
            submission.setUpdatedAt(nowInstant);
            scorecardSubmissionRepository.save(submission);
            lockedCount++;
        }

        if (lockedCount > 0) {
            log.info("Locked {} scorecard submission(s) whose interview started >{}h ago", lockedCount, LOCK_AFTER_HOURS);
        }
    }
}
