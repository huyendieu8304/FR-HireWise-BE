package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewStatus;
import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.domain.ScorecardSubmissionStatus;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BR-SCORE-03: locking a Scorecard Submission ~24h after its Interview starts. */
@ExtendWith(MockitoExtension.class)
class ScorecardLockWorkerTest {

    // "Now" is 2026-09-02T10:00:00Z.
    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");

    @Mock
    private ScorecardSubmissionRepository scorecardSubmissionRepository;

    private ScorecardLockWorker worker;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        worker = new ScorecardLockWorker(scorecardSubmissionRepository, clock);
    }

    private ScorecardSubmission submissionForInterviewAt(LocalDate date, LocalTime time) {
        Interview interview = Interview.builder().id(UUID.randomUUID())
                .application(Application.builder().id(UUID.randomUUID()).build())
                .interviewDate(date).interviewTime(time)
                .mode(InterviewMode.ONLINE).status(InterviewStatus.COMPLETED).build();
        return ScorecardSubmission.builder().id(UUID.randomUUID()).interview(interview)
                .status(ScorecardSubmissionStatus.SUBMITTED).createdAt(NOW).updatedAt(NOW).build();
    }

    @Test
    void lockOverdueSubmissions_interviewMoreThan24hAgo_getsLocked() {
        // Interview started 2026-09-01T09:00, "now" is 2026-09-02T10:00 -> 25h ago.
        ScorecardSubmission submission = submissionForInterviewAt(LocalDate.of(2026, 9, 1), LocalTime.of(9, 0));
        when(scorecardSubmissionRepository.findUnlockedWithInterviewOnOrBeforeDate(LocalDate.of(2026, 9, 2)))
                .thenReturn(List.of(submission));

        worker.lockOverdueSubmissions();

        assertThat(submission.getLockedAt()).isEqualTo(NOW);
        verify(scorecardSubmissionRepository).save(submission);
    }

    @Test
    void lockOverdueSubmissions_interviewLessThan24hAgo_staysUnlocked() {
        // Interview started 2026-09-01T11:00, "now" is 2026-09-02T10:00 -> only 23h ago.
        ScorecardSubmission submission = submissionForInterviewAt(LocalDate.of(2026, 9, 1), LocalTime.of(11, 0));
        when(scorecardSubmissionRepository.findUnlockedWithInterviewOnOrBeforeDate(LocalDate.of(2026, 9, 2)))
                .thenReturn(List.of(submission));

        worker.lockOverdueSubmissions();

        assertThat(submission.getLockedAt()).isNull();
        verify(scorecardSubmissionRepository, never()).save(any());
    }

    @Test
    void lockOverdueSubmissions_stillDraftPastWindow_alsoGetsLocked() {
        // BR-SCORE-03 keys off the interview's own time, not the submission's status.
        ScorecardSubmission draft = submissionForInterviewAt(LocalDate.of(2026, 8, 30), LocalTime.of(9, 0));
        draft.setStatus(ScorecardSubmissionStatus.DRAFT);
        when(scorecardSubmissionRepository.findUnlockedWithInterviewOnOrBeforeDate(LocalDate.of(2026, 9, 2)))
                .thenReturn(List.of(draft));

        worker.lockOverdueSubmissions();

        assertThat(draft.getLockedAt()).isNotNull();
    }

    @Test
    void lockOverdueSubmissions_noCandidates_doesNothing() {
        when(scorecardSubmissionRepository.findUnlockedWithInterviewOnOrBeforeDate(any())).thenReturn(List.of());

        worker.lockOverdueSubmissions();

        verify(scorecardSubmissionRepository, never()).save(any());
    }
}
