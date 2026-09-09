package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewStatus;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStageScorecard;
import com.hirewise.be.domain.JobStageScorecardCriterion;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.ScorecardScore;
import com.hirewise.be.domain.ScorecardStatus;
import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.domain.ScorecardSubmissionStatus;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.SaveScorecardScoresRequestDto;
import com.hirewise.be.dto.request.ScorecardScoreInputDto;
import com.hirewise.be.dto.response.ApplicationScorecardsResponseDto;
import com.hirewise.be.dto.response.ScorecardSubmissionResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ForbiddenActionException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.InterviewParticipantRepository;
import com.hirewise.be.repository.InterviewRepository;
import com.hirewise.be.repository.JobStageScorecardCriterionRepository;
import com.hirewise.be.repository.JobStageScorecardRepository;
import com.hirewise.be.repository.ScorecardScoreRepository;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-28: entering/submitting a Scorecard - BR-SCORE-01 (completeness),
 * BR-SCORE-02 (weighted score formula), BR-SCORE-03 (24h lock), and the
 * evaluator-eligibility gate (assigned Interviewer or the Job's Hiring
 * Manager). Scoring always resolves against the {@link JobStageScorecard}
 * of the Interview's (Job, Stage) pair (team decision superseding the
 * original per-Job template scoping).
 */
@ExtendWith(MockitoExtension.class)
class ScorecardSubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private static final UUID INTERVIEW_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBMISSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Long EVALUATOR_ID = 10L;
    private static final Long STAGE_ID = 50L;

    @Mock private ScorecardSubmissionRepository scorecardSubmissionRepository;
    @Mock private ScorecardScoreRepository scorecardScoreRepository;
    @Mock private JobStageScorecardCriterionRepository jobStageScorecardCriterionRepository;
    @Mock private JobStageScorecardRepository jobStageScorecardRepository;
    @Mock private InterviewRepository interviewRepository;
    @Mock private InterviewParticipantRepository interviewParticipantRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private AccessControlService accessControlService;
    @Mock private AuditLogService auditLogService;

    private ScorecardSubmissionService service;
    private final CurrentUser evaluator = new CurrentUser(EVALUATOR_ID, "int@test.com", "Interviewer", Set.of("INTERVIEWER"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ScorecardSubmissionService(scorecardSubmissionRepository, scorecardScoreRepository,
                jobStageScorecardCriterionRepository, jobStageScorecardRepository, interviewRepository,
                interviewParticipantRepository, applicationRepository, accessControlService, auditLogService, clock);
    }

    private JobPosition job(UUID jobId) {
        Department department = Department.builder().id(4L).build();
        return JobPosition.builder().id(jobId).department(department).build();
    }

    private PipelineStage stage() {
        return PipelineStage.builder().id(STAGE_ID).name("Technical Interview").stageType(StageType.INTERVIEW).build();
    }

    private Interview interview(UUID jobId) {
        Application application = Application.builder().id(UUID.randomUUID()).jobPosition(job(jobId)).build();
        return Interview.builder().id(INTERVIEW_ID).application(application).pipelineStage(stage())
                .interviewDate(LocalDate.of(2026, 9, 1)).interviewTime(LocalTime.of(9, 0))
                .mode(InterviewMode.ONLINE).status(InterviewStatus.COMPLETED).build();
    }

    private JobStageScorecard scorecard(JobPosition job, PipelineStage stage) {
        return JobStageScorecard.builder().id(UUID.randomUUID()).job(job).pipelineStage(stage).name("Tech Round")
                .version(1).status(ScorecardStatus.ACTIVE).createdAt(NOW).updatedAt(NOW).build();
    }

    private ScorecardSubmission draft(JobStageScorecard scorecard, Interview interview) {
        return ScorecardSubmission.builder().id(SUBMISSION_ID).interview(interview)
                .evaluator(User.builder().id(EVALUATOR_ID).fullName("Interviewer").build())
                .jobStageScorecard(scorecard).status(ScorecardSubmissionStatus.DRAFT)
                .createdAt(NOW).updatedAt(NOW).build();
    }

    // --- getOrCreateForm -----------------------------------------------

    @Test
    void getOrCreateForm_notAnEvaluator_throwsForbidden() {
        UUID jobId = UUID.randomUUID();
        Interview interview = interview(jobId);
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(interview));
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getOrCreateForm(INTERVIEW_ID, evaluator))
                .isInstanceOf(ForbiddenActionException.class);

        verify(scorecardSubmissionRepository, never()).save(any());
    }

    @Test
    void getOrCreateForm_interviewNotFound_throwsResourceNotFound() {
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreateForm(INTERVIEW_ID, evaluator))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrCreateForm_asAssignedInterviewer_createsNewDraftWhenNoneExists() {
        UUID jobId = UUID.randomUUID();
        Interview interview = interview(jobId);
        JobStageScorecard scorecard = scorecard(job(jobId), interview.getPipelineStage());
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(interview));
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(true);
        when(scorecardSubmissionRepository.findByInterview_IdAndEvaluator_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(Optional.empty());
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                jobId, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.of(scorecard));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of());
        when(scorecardScoreRepository.findBySubmission_Id(any())).thenReturn(List.of());

        ScorecardSubmissionResponseDto result = service.getOrCreateForm(INTERVIEW_ID, evaluator);

        verify(accessControlService).checkAccess(eq(evaluator), eq(PermissionCodes.SCORECARD_SUBMIT), any());
        ArgumentCaptor<ScorecardSubmission> captor = ArgumentCaptor.forClass(ScorecardSubmission.class);
        verify(scorecardSubmissionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ScorecardSubmissionStatus.DRAFT);
        assertThat(captor.getValue().getJobStageScorecard()).isEqualTo(scorecard);
        assertThat(result.getStatus()).isEqualTo(ScorecardSubmissionStatus.DRAFT);
        assertThat(result.getStageName()).isEqualTo("Technical Interview");
    }

    @Test
    void getOrCreateForm_noScorecardConfiguredForStage_throwsResourceNotFound() {
        UUID jobId = UUID.randomUUID();
        Interview interview = interview(jobId);
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(interview));
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(true);
        when(scorecardSubmissionRepository.findByInterview_IdAndEvaluator_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(Optional.empty());
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                jobId, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreateForm(INTERVIEW_ID, evaluator))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrCreateForm_asJobsHiringManager_allowedEvenIfNotAParticipant() {
        UUID jobId = UUID.randomUUID();
        JobPosition job = job(jobId);
        job.setHiringManager(User.builder().id(EVALUATOR_ID).build());
        PipelineStage stage = stage();
        Application application = Application.builder().id(UUID.randomUUID()).jobPosition(job).build();
        Interview interview = Interview.builder().id(INTERVIEW_ID).application(application).pipelineStage(stage)
                .interviewDate(LocalDate.of(2026, 9, 1)).interviewTime(LocalTime.of(9, 0))
                .mode(InterviewMode.ONLINE).status(InterviewStatus.COMPLETED).build();
        JobStageScorecard scorecard = scorecard(job, stage);
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(interview));
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(false);
        when(scorecardSubmissionRepository.findByInterview_IdAndEvaluator_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(Optional.of(draft(scorecard, interview)));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of());
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());

        ScorecardSubmissionResponseDto result = service.getOrCreateForm(INTERVIEW_ID, evaluator);

        assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
        verify(scorecardSubmissionRepository, never()).save(any()); // reused existing draft, no duplicate
    }

    @Test
    void getOrCreateForm_existingSubmission_reusesItInsteadOfCreatingDuplicate() {
        UUID jobId = UUID.randomUUID();
        Interview interview = interview(jobId);
        JobStageScorecard scorecard = scorecard(job(jobId), interview.getPipelineStage());
        ScorecardSubmission existing = draft(scorecard, interview);
        when(interviewRepository.findById(INTERVIEW_ID)).thenReturn(Optional.of(interview));
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(true);
        when(scorecardSubmissionRepository.findByInterview_IdAndEvaluator_Id(INTERVIEW_ID, EVALUATOR_ID))
                .thenReturn(Optional.of(existing));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of());
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());

        service.getOrCreateForm(INTERVIEW_ID, evaluator);

        verify(scorecardSubmissionRepository, never()).save(any());
        verify(jobStageScorecardRepository, never())
                .findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(any(), any(), any());
    }

    // --- getSubmissionDetail (read-only, ANY viewer with APPLICATION_VIEW) ---

    @Test
    void getSubmissionDetail_notFound_throwsResourceNotFound() {
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissionDetail(SUBMISSION_ID, evaluator))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSubmissionDetail_checksApplicationViewNotEvaluatorEligibility() {
        // The whole point of this endpoint: a viewer who is NEITHER an assigned Interviewer
        // NOR the Hiring Manager (would fail checkEvaluatorEligible) can still view - only
        // APPLICATION_VIEW is checked, never the evaluator-only eligibility rule.
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        CurrentUser recruiter = new CurrentUser(77L, "recruiter@test.com", "Recruiter", Set.of("RECRUITER"));
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of());
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());

        ScorecardSubmissionResponseDto result = service.getSubmissionDetail(SUBMISSION_ID, recruiter);

        verify(accessControlService).checkAccess(eq(recruiter), eq(PermissionCodes.APPLICATION_VIEW), any());
        verify(interviewParticipantRepository, never()).existsByInterview_IdAndInterviewer_Id(any(), any());
        assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    }

    // --- saveProgress / submit ------------------------------------------

    @Test
    void saveProgress_locked_throwsBusinessConflict() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission locked = draft(scorecard, interview);
        locked.setLockedAt(NOW);
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(locked));

        SaveScorecardScoresRequestDto request = new SaveScorecardScoresRequestDto("ok", List.of());

        assertThatThrownBy(() -> service.saveProgress(SUBMISSION_ID, request, evaluator))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void saveProgress_criterionNotInTemplate_throwsBadRequest() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of()); // scorecard has NO criteria - any criterionId is foreign

        SaveScorecardScoresRequestDto request = new SaveScorecardScoresRequestDto(
                "ok", List.of(new ScorecardScoreInputDto(UUID.randomUUID(), BigDecimal.ONE, "note")));

        assertThatThrownBy(() -> service.saveProgress(SUBMISSION_ID, request, evaluator))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void saveProgress_valid_upsertsScoresAndOverallComment() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        JobStageScorecardCriterion criterion = JobStageScorecardCriterion.builder().id(UUID.randomUUID())
                .jobStageScorecard(scorecard).name("Depth").weight(BigDecimal.TEN).maxScore(BigDecimal.valueOf(5))
                .position(1).required(true).build();
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of(criterion));

        SaveScorecardScoresRequestDto request = new SaveScorecardScoresRequestDto(
                "Solid candidate", List.of(new ScorecardScoreInputDto(criterion.getId(), BigDecimal.valueOf(4), "good")));

        service.saveProgress(SUBMISSION_ID, request, evaluator);

        assertThat(submission.getOverallComment()).isEqualTo("Solid candidate");
        assertThat(submission.getStatus()).isEqualTo(ScorecardSubmissionStatus.DRAFT); // saving progress never auto-submits
        verify(scorecardScoreRepository).save(any(ScorecardScore.class));
    }

    @Test
    void saveProgress_scoreExceedsCriterionMaxScore_throwsBadRequest() {
        // US-INT-02/EX: điểm nhập vào không được vượt quá thang điểm tối đa của chính tiêu chí đó -
        // giới hạn này khác nhau theo từng criterion nên không thể validate tĩnh ở tầng DTO.
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        JobStageScorecardCriterion criterion = JobStageScorecardCriterion.builder().id(UUID.randomUUID())
                .jobStageScorecard(scorecard).name("Depth").weight(BigDecimal.TEN).maxScore(BigDecimal.valueOf(5))
                .position(1).required(true).build();
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of(criterion));

        SaveScorecardScoresRequestDto request = new SaveScorecardScoresRequestDto(
                "ok", List.of(new ScorecardScoreInputDto(criterion.getId(), BigDecimal.valueOf(99), "bad")));

        assertThatThrownBy(() -> service.saveProgress(SUBMISSION_ID, request, evaluator))
                .isInstanceOf(BadRequestException.class);
        verify(scorecardScoreRepository, never()).save(any());
    }

    @Test
    void submit_missingRequiredCriterionScore_throwsBadRequest() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        submission.setOverallComment("some comment");
        JobStageScorecardCriterion required = JobStageScorecardCriterion.builder().id(UUID.randomUUID())
                .jobStageScorecard(scorecard).name("Depth").weight(BigDecimal.TEN).maxScore(BigDecimal.valueOf(5))
                .position(1).required(true).build();
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of(required));
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of()); // never scored

        assertThatThrownBy(() -> service.submit(SUBMISSION_ID, evaluator)).isInstanceOf(BadRequestException.class);
        verify(scorecardSubmissionRepository, never()).save(argThat(s -> s.getStatus() == ScorecardSubmissionStatus.SUBMITTED));
    }

    @Test
    void submit_blankOverallComment_throwsBadRequest() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        submission.setOverallComment("   ");
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of());
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(SUBMISSION_ID, evaluator)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void submit_locked_throwsBusinessConflict() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        submission.setLockedAt(NOW);
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.submit(SUBMISSION_ID, evaluator)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void submit_valid_computesWeightedScoreOnlyOverScoredCriteriaAndMarksSubmitted() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        submission.setOverallComment("Great fit");

        JobStageScorecardCriterion required = JobStageScorecardCriterion.builder().id(UUID.randomUUID())
                .jobStageScorecard(scorecard).name("Depth").weight(BigDecimal.valueOf(60)).maxScore(BigDecimal.valueOf(5))
                .position(1).required(true).build();
        // Optional criterion deliberately left unscored - must NOT count as 0 in the average.
        JobStageScorecardCriterion optional = JobStageScorecardCriterion.builder().id(UUID.randomUUID())
                .jobStageScorecard(scorecard).name("Culture fit").weight(BigDecimal.valueOf(40)).maxScore(BigDecimal.valueOf(5))
                .position(2).required(false).build();

        ScorecardScore requiredScore = ScorecardScore.builder().id(UUID.randomUUID())
                .submission(submission).criterion(required).score(BigDecimal.valueOf(4)).createdAt(NOW).updatedAt(NOW).build();

        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(jobStageScorecardCriterionRepository.findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId()))
                .thenReturn(List.of(required, optional));
        when(scorecardScoreRepository.findBySubmission_Id(SUBMISSION_ID)).thenReturn(List.of(requiredScore));

        service.submit(SUBMISSION_ID, evaluator);

        // weighted = (4 * 60) / 60 = 4.00 - the unscored optional criterion contributes to neither side.
        assertThat(submission.getWeightedScore()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(submission.getStatus()).isEqualTo(ScorecardSubmissionStatus.SUBMITTED);
        assertThat(submission.getSubmittedAt()).isEqualTo(NOW);
    }

    // --- unlock ----------------------------------------------------------

    @Test
    void unlock_notLocked_throwsBusinessConflict() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        CurrentUser hrAdmin = new CurrentUser(99L, "hr@test.com", "HR Admin", Set.of("HR_ADMIN"));

        assertThatThrownBy(() -> service.unlock(SUBMISSION_ID, hrAdmin)).isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void unlock_valid_clearsLockedAtAndWritesAuditLog() {
        Interview interview = interview(UUID.randomUUID());
        JobStageScorecard scorecard = scorecard(job(UUID.randomUUID()), interview.getPipelineStage());
        ScorecardSubmission submission = draft(scorecard, interview);
        submission.setLockedAt(NOW);
        when(scorecardSubmissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        CurrentUser hrAdmin = new CurrentUser(99L, "hr@test.com", "HR Admin", Set.of("HR_ADMIN"));

        service.unlock(SUBMISSION_ID, hrAdmin);

        verify(accessControlService).checkAccess(eq(hrAdmin), eq(PermissionCodes.SCORECARD_UNLOCK), any());
        assertThat(submission.getLockedAt()).isNull();
        verify(auditLogService).record(eq(99L), eq("SCORECARD_SUBMISSION_UNLOCKED"),
                eq("scorecard_submissions"), eq(SUBMISSION_ID.toString()));
    }

    // --- listForApplication -----------------------------------------------

    @Test
    void listForApplication_averagesOnlySubmittedSubmissionsAcrossAllInterviews() {
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        PipelineStage phoneScreenStage = PipelineStage.builder().id(51L).name("Phone Screen").stageType(StageType.INTERVIEW).build();
        PipelineStage finalStage = PipelineStage.builder().id(52L).name("Final Interview").stageType(StageType.INTERVIEW).build();
        Interview interviewA = Interview.builder().id(UUID.randomUUID()).application(application).pipelineStage(phoneScreenStage)
                .interviewDate(LocalDate.of(2026, 9, 1)).interviewTime(LocalTime.of(9, 0))
                .mode(InterviewMode.ONLINE).status(InterviewStatus.COMPLETED).build();
        Interview interviewB = Interview.builder().id(UUID.randomUUID()).application(application).pipelineStage(finalStage)
                .interviewDate(LocalDate.of(2026, 9, 2)).interviewTime(LocalTime.of(9, 0))
                .mode(InterviewMode.ONSITE).status(InterviewStatus.COMPLETED).build();

        ScorecardSubmission submitted1 = ScorecardSubmission.builder().id(UUID.randomUUID()).interview(interviewA)
                .evaluator(User.builder().id(1L).fullName("A").build()).jobStageScorecard(scorecard(job, phoneScreenStage))
                .status(ScorecardSubmissionStatus.SUBMITTED).weightedScore(new BigDecimal("4.00"))
                .createdAt(NOW).updatedAt(NOW).build();
        ScorecardSubmission submitted2 = ScorecardSubmission.builder().id(UUID.randomUUID()).interview(interviewB)
                .evaluator(User.builder().id(2L).fullName("B").build()).jobStageScorecard(scorecard(job, finalStage))
                .status(ScorecardSubmissionStatus.SUBMITTED).weightedScore(new BigDecimal("2.00"))
                .createdAt(NOW).updatedAt(NOW).build();
        ScorecardSubmission stillDraft = ScorecardSubmission.builder().id(UUID.randomUUID()).interview(interviewB)
                .evaluator(User.builder().id(3L).fullName("C").build()).jobStageScorecard(scorecard(job, finalStage))
                .status(ScorecardSubmissionStatus.DRAFT).createdAt(NOW).updatedAt(NOW).build();

        when(interviewRepository.findByApplication_IdFetchDetails(applicationId))
                .thenReturn(List.of(interviewA, interviewB));
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId))
                .thenReturn(List.of(submitted1, submitted2, stillDraft));

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        // (4.00 + 2.00) / 2 = 3.00 - the still-DRAFT submission does not count.
        assertThat(result.getAverageWeightedScore()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(result.getInterviews()).hasSize(2);
        assertThat(result.getInterviews()).extracting(g -> g.getStageName())
                .containsExactlyInAnyOrder("Phone Screen", "Final Interview");
    }

    @Test
    void listForApplication_noSubmittedSubmissionsYet_averageIsNull() {
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(interviewRepository.findByApplication_IdFetchDetails(applicationId)).thenReturn(List.of());
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId))
                .thenReturn(List.of());

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        assertThat(result.getAverageWeightedScore()).isNull();
        assertThat(result.getInterviews()).isEmpty();
    }

    @Test
    void listForApplication_interviewWithNoSubmissionsYetStillAppears() {
        // Bug fix regression: an Interview nobody has opened to score yet (0 rows in
        // scorecard_submissions) must still show up, with an empty submissions list - it's
        // the ONLY way an evaluator can find the "Cham diem cua toi" entry point at all.
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        PipelineStage stage = PipelineStage.builder().id(60L).name("Technical Interview").stageType(StageType.INTERVIEW).build();
        Interview freshInterview = Interview.builder().id(UUID.randomUUID()).application(application).pipelineStage(stage)
                .interviewDate(LocalDate.of(2026, 9, 10)).interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE).status(InterviewStatus.SCHEDULED).build();

        when(interviewRepository.findByApplication_IdFetchDetails(applicationId)).thenReturn(List.of(freshInterview));
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId))
                .thenReturn(List.of()); // nobody has started scoring this interview yet

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        assertThat(result.getInterviews()).hasSize(1);
        assertThat(result.getInterviews().get(0).getStageName()).isEqualTo("Technical Interview");
        assertThat(result.getInterviews().get(0).getSubmissions()).isEmpty();
    }

    @Test
    void listForApplication_currentUserIsAssignedInterviewer_currentUserCanScoreTrue() {
        // FE hides the "Cham diem" action entirely when this is false - must be true for
        // whoever is actually an assigned Interviewer of the Interview.
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        Interview interview = interview(job.getId());
        // interview(...) builds its own JobPosition internally - reuse just its pipelineStage/dates
        // by wiring it against THIS test's `application` instead, so job() matches exactly.
        Interview scoped = Interview.builder().id(interview.getId()).application(application)
                .pipelineStage(interview.getPipelineStage()).interviewDate(interview.getInterviewDate())
                .interviewTime(interview.getInterviewTime()).mode(interview.getMode()).status(InterviewStatus.SCHEDULED).build();

        when(interviewRepository.findByApplication_IdFetchDetails(applicationId)).thenReturn(List.of(scoped));
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId)).thenReturn(List.of());
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(scoped.getId(), EVALUATOR_ID))
                .thenReturn(true);

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        verify(accessControlService).checkAccess(eq(evaluator), eq(PermissionCodes.SCORECARD_SUBMIT), any());
        assertThat(result.getInterviews().get(0).isCurrentUserCanScore()).isTrue();
    }

    @Test
    void listForApplication_currentUserNeitherParticipantNorHiringManager_currentUserCanScoreFalse() {
        // A viewer with only APPLICATION_VIEW (e.g. Recruiter, HR Admin, or an Interviewer
        // not assigned to THIS specific Interview) must not see the action offered at all.
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        Interview interview = interview(job.getId());
        Interview scoped = Interview.builder().id(interview.getId()).application(application)
                .pipelineStage(interview.getPipelineStage()).interviewDate(interview.getInterviewDate())
                .interviewTime(interview.getInterviewTime()).mode(interview.getMode()).status(InterviewStatus.SCHEDULED).build();

        when(interviewRepository.findByApplication_IdFetchDetails(applicationId)).thenReturn(List.of(scoped));
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId)).thenReturn(List.of());
        when(interviewParticipantRepository.existsByInterview_IdAndInterviewer_Id(scoped.getId(), EVALUATOR_ID))
                .thenReturn(false);
        // job(...) never sets a hiringManager, so isHiringManager is false too by construction.

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        assertThat(result.getInterviews().get(0).isCurrentUserCanScore()).isFalse();
    }

    @Test
    void listForApplication_cancelledInterview_excludedFromList() {
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        PipelineStage stage = PipelineStage.builder().id(61L).name("Phone Screen").stageType(StageType.INTERVIEW).build();
        Interview cancelled = Interview.builder().id(UUID.randomUUID()).application(application).pipelineStage(stage)
                .interviewDate(LocalDate.of(2026, 9, 10)).interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE).status(InterviewStatus.CANCELLED).build();

        when(interviewRepository.findByApplication_IdFetchDetails(applicationId)).thenReturn(List.of(cancelled));
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId))
                .thenReturn(List.of());

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        assertThat(result.getInterviews()).isEmpty();
    }

    @Test
    void listForApplication_cancelledInterviewWithExistingSubmission_stillAppears() {
        // Bug fix regression: KanbanService auto-CANCELs a still-SCHEDULED Interview when the
        // candidate moves off an INTERVIEW-type Stage (e.g. dragged to Offer) - but if that
        // Interview had ALREADY been scored (a real SUBMITTED submission with a weighted_score
        // exists), that history must NOT disappear from the tab just because the Interview's
        // status flipped afterwards. Only a cancelled Interview with ZERO submissions should
        // be excluded (see listForApplication_cancelledInterview_excludedFromList).
        UUID applicationId = UUID.randomUUID();
        JobPosition job = job(UUID.randomUUID());
        Application application = Application.builder().id(applicationId).jobPosition(job).build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        PipelineStage stage = PipelineStage.builder().id(62L).name("Technical Interview").stageType(StageType.INTERVIEW).build();
        Interview cancelledButScored = Interview.builder().id(UUID.randomUUID()).application(application).pipelineStage(stage)
                .interviewDate(LocalDate.of(2026, 9, 9)).interviewTime(LocalTime.of(8, 30))
                .mode(InterviewMode.ONLINE).status(InterviewStatus.CANCELLED).build();

        ScorecardSubmission submitted = ScorecardSubmission.builder().id(UUID.randomUUID()).interview(cancelledButScored)
                .evaluator(User.builder().id(EVALUATOR_ID).fullName("Interviewer").build())
                .jobStageScorecard(scorecard(job, stage)).status(ScorecardSubmissionStatus.SUBMITTED)
                .weightedScore(new BigDecimal("4.00")).createdAt(NOW).updatedAt(NOW).build();

        when(interviewRepository.findByApplication_IdFetchDetails(applicationId)).thenReturn(List.of(cancelledButScored));
        when(scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId))
                .thenReturn(List.of(submitted));

        ApplicationScorecardsResponseDto result = service.listForApplication(applicationId, evaluator);

        assertThat(result.getInterviews()).hasSize(1);
        assertThat(result.getInterviews().get(0).getStageName()).isEqualTo("Technical Interview");
        assertThat(result.getInterviews().get(0).getSubmissions()).hasSize(1);
        assertThat(result.getAverageWeightedScore()).isEqualByComparingTo(new BigDecimal("4.00"));
    }
}
