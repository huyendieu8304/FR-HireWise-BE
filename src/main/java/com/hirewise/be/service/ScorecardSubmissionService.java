package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewStatus;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStageScorecard;
import com.hirewise.be.domain.JobStageScorecardCriterion;
import com.hirewise.be.domain.ScorecardScore;
import com.hirewise.be.domain.ScorecardStatus;
import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.domain.ScorecardSubmissionStatus;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.SaveScorecardScoresRequestDto;
import com.hirewise.be.dto.request.ScorecardScoreInputDto;
import com.hirewise.be.dto.response.ApplicationScorecardsResponseDto;
import com.hirewise.be.dto.response.InterviewScorecardGroupDto;
import com.hirewise.be.dto.response.ScorecardSubmissionResponseDto;
import com.hirewise.be.dto.response.ScorecardSubmissionSummaryDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ForbiddenActionException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.ScorecardMapper;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.InterviewParticipantRepository;
import com.hirewise.be.repository.InterviewRepository;
import com.hirewise.be.repository.JobStageScorecardCriterionRepository;
import com.hirewise.be.repository.JobStageScorecardRepository;
import com.hirewise.be.repository.ScorecardScoreRepository;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-28: an Interviewer/Hiring Manager rates a Candidate through 1
 * {@link ScorecardSubmission} per (Interview, evaluator) pair, scored
 * against the {@link JobStageScorecard} configured for that Interview's
 * (Job, Stage) pair - see {@code Interview#pipelineStage}'s own Javadoc for
 * how that pair is anchored at schedule time.
 * <p>
 * {@link #getOrCreateForm} is the one deliberate exception to "a GET must
 * never mutate data" in this codebase: the very first time an evaluator
 * opens the Scorecard tab for an Interview there is, by definition, no
 * {@code submission_id} yet for {@code @RequiresOwnership} to key off of on
 * a later save/submit call - lazily creating an empty {@code DRAFT} row here
 * gives every subsequent action (save progress, submit) a real id to own,
 * exactly like {@code OwnershipPolicyRegistry}'s pre-existing
 * {@code SCORECARD_SUBMIT} entries assume. The alternative (a separate
 * "start scoring" POST before the form can even render) adds a network
 * round-trip for zero benefit - opening the tab already implies intent to score.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ScorecardSubmissionService {

    ScorecardSubmissionRepository scorecardSubmissionRepository;
    ScorecardScoreRepository scorecardScoreRepository;
    JobStageScorecardCriterionRepository jobStageScorecardCriterionRepository;
    JobStageScorecardRepository jobStageScorecardRepository;
    InterviewRepository interviewRepository;
    InterviewParticipantRepository interviewParticipantRepository;
    ApplicationRepository applicationRepository;
    AccessControlService accessControlService;
    AuditLogService auditLogService;
    Clock clock;

    /**
     * UC-28 step 1: the current user's own Scorecard form for 1 Interview -
     * auto-creates an empty {@code DRAFT} submission on first open (see
     * class Javadoc).
     *
     * @param interviewId id of the Interview being scored
     * @param currentUser authenticated caller - must have {@code SCORECARD_SUBMIT}
     *                    scoped to the Interview's Job's department, AND be either
     *                    an assigned Interviewer or the Job's Hiring Manager (EX: neither)
     * @throws ResourceNotFoundException if no Interview exists with {@code interviewId},
     *                                    the Interview has no {@code pipelineStage} captured
     *                                    (legacy row, predates UC-27 v2), or no {@code ACTIVE}
     *                                    Scorecard is configured for its (Job, Stage) pair
     * @throws ForbiddenActionException  if the caller is not an evaluator of this Interview
     */
    @Transactional
    public ScorecardSubmissionResponseDto getOrCreateForm(UUID interviewId, CurrentUser currentUser) {
        Interview interview = findInterviewOrThrow(interviewId);
        JobPosition job = interview.getApplication().getJobPosition();
        checkEvaluatorEligible(interview, job, currentUser);

        ScorecardSubmission submission = scorecardSubmissionRepository
                .findByInterview_IdAndEvaluator_Id(interviewId, currentUser.userId())
                .orElseGet(() -> createDraft(interview, job, currentUser));

        return buildResponseDto(submission);
    }

    /**
     * Read-only detail of ANY submission for whoever has {@code APPLICATION_VIEW}
     * on its Application - deliberately NOT gated by
     * {@link #checkEvaluatorEligible} (that check is about who may SCORE,
     * this one is about who may merely VIEW what has already been scored).
     * Lets a Recruiter/HR Admin, or one Interviewer looking at a colleague's
     * submission, open the same read-only view the FE renders for
     * {@code currentUserCanScore = false} rows on the Scorecard tab -
     * requested after the team noticed hiding the "Cham diem" action
     * entirely also silently removed any way to inspect an existing
     * evaluation's criteria/comments.
     *
     * @param submissionId id of the submission to view
     * @param currentUser  authenticated caller, must have {@code APPLICATION_VIEW}
     *                     scoped to the submission's Interview's Job's department
     * @throws ResourceNotFoundException if no submission exists with {@code submissionId}
     */
    public ScorecardSubmissionResponseDto getSubmissionDetail(UUID submissionId, CurrentUser currentUser) {
        ScorecardSubmission submission = findSubmissionOrThrow(submissionId);
        JobPosition job = submission.getInterview().getApplication().getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW,
                ResourceContext.job(job.getId(), departmentId));
        return buildResponseDto(submission);
    }

    /**
     * UC-28 step 2-3: saves progress (scores + overall comment) WITHOUT
     * submitting - callable any number of times while the submission is
     * still editable. BR-SCORE-01 is intentionally NOT enforced here, only
     * at {@link #submit}.
     *
     * @param submissionId id of the submission being edited (from {@link #getOrCreateForm})
     * @param request      every criterion's current score/comment + the overall comment
     * @param currentUser  authenticated caller - {@code @RequiresOwnership} on the controller
     *                     already confirmed this is their own submission
     * @throws ResourceNotFoundException if no submission exists with {@code submissionId}
     * @throws BusinessConflictException if the submission is locked (BR-SCORE-03)
     * @throws BadRequestException       if a {@code criterionId} doesn't belong to this
     *                                    submission's (Job, Stage) Scorecard, or a score
     *                                    exceeds that criterion's own {@code max_score}
     *                                    (US-INT-02 EX)
     */
    @Transactional
    public ScorecardSubmissionResponseDto saveProgress(
            UUID submissionId, SaveScorecardScoresRequestDto request, CurrentUser currentUser) {
        ScorecardSubmission submission = findSubmissionOrThrow(submissionId);
        requireNotLocked(submission);

        applyScores(submission, request);
        submission.setOverallComment(request.getOverallComment());
        submission.setUpdatedAt(Instant.now(clock));
        scorecardSubmissionRepository.save(submission);

        log.info("Saved scorecard progress: submission={} evaluator={}", submissionId, currentUser.userId());
        return buildResponseDto(submission);
    }

    /**
     * UC-28 step 4-5: validates BR-SCORE-01, computes the Weighted Score
     * (BR-SCORE-02), and finalizes the submission as {@code SUBMITTED}.
     * Re-submitting an already-{@code SUBMITTED} (but not yet locked)
     * submission is allowed and simply recomputes everything fresh - see
     * class-level design note in the accompanying explain doc for why no
     * separate "SUBMITTED blocks further edits" rule is enforced here.
     *
     * @param submissionId id of the submission to finalize
     * @param currentUser  authenticated caller - {@code @RequiresOwnership} already
     *                     confirmed this is their own submission
     * @throws ResourceNotFoundException if no submission exists with {@code submissionId}
     * @throws BusinessConflictException if the submission is locked (BR-SCORE-03)
     * @throws BadRequestException       if a required criterion has no score, or the
     *                                    overall comment is blank (BR-SCORE-01, EX-01/ME-29)
     */
    @Transactional
    public ScorecardSubmissionResponseDto submit(UUID submissionId, CurrentUser currentUser) {
        ScorecardSubmission submission = findSubmissionOrThrow(submissionId);
        requireNotLocked(submission);

        List<JobStageScorecardCriterion> criteria = jobStageScorecardCriterionRepository
                .findByJobStageScorecard_IdOrderByPositionAsc(submission.getJobStageScorecard().getId());
        Map<UUID, ScorecardScore> scoresByCriterion = scoresByCriterionId(submissionId);

        validateComplete(criteria, scoresByCriterion, submission.getOverallComment());
        BigDecimal weightedScore = computeWeightedScore(criteria, scoresByCriterion);

        Instant now = Instant.now(clock);
        submission.setWeightedScore(weightedScore);
        submission.setStatus(ScorecardSubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);
        scorecardSubmissionRepository.save(submission);

        log.info("Submitted scorecard: submission={} evaluator={} weightedScore={}",
                submissionId, currentUser.userId(), weightedScore);
        return buildResponseDto(submission);
    }

    /**
     * BR-SCORE-03: HR Admin-only override to clear {@code locked_at} so a
     * correction can be made after the normal 24h editing window has
     * passed. Every use is audited (who, when, which submission).
     *
     * @param submissionId id of the locked submission to unlock
     * @param currentUser  authenticated caller, must have {@code SCORECARD_UNLOCK}
     *                     (HR Admin only - see {@code V39} migration)
     * @throws ResourceNotFoundException if no submission exists with {@code submissionId}
     * @throws BusinessConflictException if the submission is not currently locked
     */
    @Transactional
    public void unlock(UUID submissionId, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_UNLOCK, ResourceContext.none());
        ScorecardSubmission submission = findSubmissionOrThrow(submissionId);
        if (submission.getLockedAt() == null) {
            throw new BusinessConflictException(ErrorCode.SCORECARD_SUBMISSION_NOT_LOCKED);
        }

        submission.setLockedAt(null);
        submission.setUpdatedAt(Instant.now(clock));
        scorecardSubmissionRepository.save(submission);

        auditLogService.record(currentUser.userId(), "SCORECARD_SUBMISSION_UNLOCKED",
                "scorecard_submissions", submissionId.toString());
        log.info("Scorecard submission {} unlocked by HR Admin userId={}", submissionId, currentUser.userId());
    }

    /**
     * UC-28 step 6: the Applicant Card [Scorecard] tab - every non-cancelled
     * Interview of this Application grouped with each evaluator's result
     * (each group labeled with its own Stage name, since each
     * Interview-type Stage has its own independent Scorecard - team
     * decision superseding the original per-Job scoping), plus the average
     * Weighted Score across every {@code SUBMITTED} submission of the whole
     * Application.
     * <p>
     * The group list is sourced from {@code interviewRepository} (every
     * Interview of the Application), NOT from
     * {@code scorecardSubmissionRepository} - an Interview with zero
     * submissions so far (nobody has opened it to score yet) must still
     * appear so the evaluator has something to click "Cham diem cua toi"
     * on. Deriving groups from submissions alone was a real bug this
     * feature shipped with: a freshly-scheduled Interview that nobody had
     * scored yet would never show up in this list at all, since
     * {@code scorecard_submissions} only gets its first row lazily on
     * {@link #getOrCreateForm} - a chicken-and-egg gap where the entry
     * point to start scoring was itself invisible until scoring had
     * already started.
     *
     * @param applicationId id of the Application
     * @param currentUser   authenticated caller, must have {@code APPLICATION_VIEW}
     *                      scoped to the Application's Job's department
     * @throws ResourceNotFoundException if no Application exists with {@code applicationId}
     */
    public ApplicationScorecardsResponseDto listForApplication(UUID applicationId, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));
        JobPosition job = application.getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW,
                ResourceContext.job(job.getId(), departmentId));

        List<ScorecardSubmission> submissions =
                scorecardSubmissionRepository.findByInterview_Application_IdFetchDetails(applicationId);
        Map<UUID, List<ScorecardSubmission>> submissionsByInterview = submissions.stream()
                .collect(Collectors.groupingBy(s -> s.getInterview().getId()));

        // Nguồn danh sách nhóm PHẢI là mọi Interview của Application (không phải chỉ những
        // Interview đã CÓ submission) - nếu không, 1 Interview vừa đặt lịch, chưa ai bấm
        // "Chấm điểm của tôi" lần nào, sẽ KHÔNG BAO GIỜ xuất hiện trong tab này (vì chưa có
        // scorecard_submissions row nào), khiến không ai có cách nào bấm vào để BẮT ĐẦU chấm
        // điểm - một vòng luẩn quẩn "cần submission để hiện, cần hiện để tạo submission".
        //
        // Loại CANCELLED chỉ khi nó CHƯA từng có submission nào - KanbanService tự động set
        // 1 Interview còn SCHEDULED thành CANCELLED khi ứng viên rời khỏi Stage loại INTERVIEW
        // (vd kéo Kanban card sang Offer) - nhưng nếu Interview đó đã được chấm điểm THẬT
        // (weighted_score đã tính, đã SUBMITTED) trước khi bị hủy, dữ liệu đánh giá đó VẪN
        // là lịch sử có thật, không được biến mất khỏi tab chỉ vì trạng thái Interview đổi
        // SAU KHI đã chấm xong - bug thật đã gặp: "chấm xong, kéo card sang Offer, mất luôn
        // nút Xem chi tiết dù điểm vẫn tính vào averageWeightedScore ở trên".
        List<Interview> interviews = interviewRepository.findByApplication_IdFetchDetails(applicationId).stream()
                .filter(i -> i.getStatus() != InterviewStatus.CANCELLED || submissionsByInterview.containsKey(i.getId()))
                .toList();

        List<InterviewScorecardGroupDto> groups = interviews.stream()
                .map(interview -> {
                    List<ScorecardSubmissionSummaryDto> rows = submissionsByInterview
                            .getOrDefault(interview.getId(), List.of()).stream()
                            .map(s -> ScorecardSubmissionSummaryDto.builder()
                                    .submissionId(s.getId())
                                    .evaluatorId(s.getEvaluator().getId())
                                    .evaluatorName(s.getEvaluator().getFullName())
                                    .status(s.getStatus())
                                    .weightedScore(s.getWeightedScore())
                                    .submittedAt(s.getSubmittedAt())
                                    .locked(s.getLockedAt() != null)
                                    .currentUser(s.getEvaluator().getId().equals(currentUser.userId()))
                                    .build())
                            .toList();
                    return InterviewScorecardGroupDto.builder()
                            .interviewId(interview.getId())
                            .stageName(interview.getPipelineStage() != null ? interview.getPipelineStage().getName() : null)
                            .interviewDate(interview.getInterviewDate())
                            .interviewTime(interview.getInterviewTime())
                            .mode(interview.getMode())
                            .status(interview.getStatus())
                            .submissions(rows)
                            .currentUserCanScore(canCurrentUserScore(interview, job, currentUser))
                            .build();
                })
                .sorted(Comparator.comparing(InterviewScorecardGroupDto::getInterviewDate).reversed()
                        .thenComparing(Comparator.comparing(InterviewScorecardGroupDto::getInterviewTime).reversed()))
                .toList();

        BigDecimal average = averageWeightedScore(submissions);
        return ApplicationScorecardsResponseDto.builder()
                .averageWeightedScore(average)
                .interviews(groups)
                .build();
    }

    // ---------------------------------------------------------------------

    private ScorecardSubmission createDraft(Interview interview, JobPosition job, CurrentUser currentUser) {
        JobStageScorecard scorecard = resolveJobStageScorecard(job, interview);
        Instant now = Instant.now(clock);
        ScorecardSubmission submission = ScorecardSubmission.builder()
                .id(UUID.randomUUID())
                .interview(interview)
                .evaluator(User.builder().id(currentUser.userId()).build())
                .jobStageScorecard(scorecard)
                .status(ScorecardSubmissionStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        scorecardSubmissionRepository.save(submission);
        log.info("Created draft scorecard submission {} (interview={}, evaluator={}, jobStageScorecard={})",
                submission.getId(), interview.getId(), currentUser.userId(), scorecard.getId());
        return submission;
    }

    /**
     * UC-28 precondition: the Scorecard configured for this Interview's
     * (Job, Stage) pair - the pair is fixed at schedule time
     * ({@code interview.pipelineStage}), NOT the Application's current stage
     * (which may have moved on since), so an Interview always scores
     * against the same Scorecard no matter how far the candidate has since
     * progressed.
     */
    private JobStageScorecard resolveJobStageScorecard(JobPosition job, Interview interview) {
        if (interview.getPipelineStage() == null) {
            throw new ResourceNotFoundException(ErrorCode.JOB_STAGE_SCORECARD_NOT_FOUND, interview.getId());
        }
        return jobStageScorecardRepository
                .findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                        job.getId(), interview.getPipelineStage().getId(), ScorecardStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.JOB_STAGE_SCORECARD_NOT_FOUND, interview.getPipelineStage().getId()));
    }

    /** UC-28 precondition: caller must be an assigned Interviewer of this Interview, or the Job's Hiring Manager. */
    private void checkEvaluatorEligible(Interview interview, JobPosition job, CurrentUser currentUser) {
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_SUBMIT,
                ResourceContext.job(job.getId(), departmentId));

        boolean isParticipant = interviewParticipantRepository
                .existsByInterview_IdAndInterviewer_Id(interview.getId(), currentUser.userId());
        boolean isHiringManager = job.getHiringManager() != null
                && job.getHiringManager().getId().equals(currentUser.userId());
        if (!isParticipant && !isHiringManager) {
            throw new ForbiddenActionException(ErrorCode.SCORECARD_NOT_AN_EVALUATOR);
        }
    }

    /**
     * Non-throwing mirror of {@link #checkEvaluatorEligible} - a pure UI
     * hint for {@link #listForApplication} (whether to offer the "Cham
     * diem" action for this Interview at all), never itself a security
     * boundary - {@link #getOrCreateForm} still runs the real,
     * exception-throwing check on the actual scoring endpoints regardless
     * of what this returns. Swallows any exception from the Layer 2/3
     * check (permission denied, out of scope, ...) as simply "can't score",
     * since nothing here should ever surface as an error to a caller who
     * only asked to VIEW the Scorecard tab.
     */
    private boolean canCurrentUserScore(Interview interview, JobPosition job, CurrentUser currentUser) {
        try {
            Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
            accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_SUBMIT,
                    ResourceContext.job(job.getId(), departmentId));
        } catch (RuntimeException e) {
            return false;
        }
        boolean isParticipant = interviewParticipantRepository
                .existsByInterview_IdAndInterviewer_Id(interview.getId(), currentUser.userId());
        boolean isHiringManager = job.getHiringManager() != null
                && job.getHiringManager().getId().equals(currentUser.userId());
        return isParticipant || isHiringManager;
    }

    private void applyScores(ScorecardSubmission submission, SaveScorecardScoresRequestDto request) {
        Map<UUID, ScorecardScore> existingByCriterion = scoresByCriterionId(submission.getId());
        Map<UUID, JobStageScorecardCriterion> criteriaById = jobStageScorecardCriterionRepository
                .findByJobStageScorecard_IdOrderByPositionAsc(submission.getJobStageScorecard().getId())
                .stream().collect(Collectors.toMap(JobStageScorecardCriterion::getId, Function.identity()));

        Instant now = Instant.now(clock);
        for (ScorecardScoreInputDto input : request.getScores()) {
            JobStageScorecardCriterion criterion = criteriaById.get(input.getCriterionId());
            if (criterion == null) {
                throw new BadRequestException(ErrorCode.SCORECARD_CRITERION_NOT_IN_TEMPLATE);
            }
            // BR-SCORE-01/02: the negative case is already rejected at the DTO level
            // (@DecimalMin); the upper bound is per-criterion (max_score varies), so it
            // can only be checked here once the actual criterion has been looked up.
            if (input.getScore() != null && input.getScore().compareTo(criterion.getMaxScore()) > 0) {
                throw new BadRequestException(ErrorCode.SCORECARD_SCORE_EXCEEDS_MAX,
                        criterion.getName(), criterion.getMaxScore());
            }
            ScorecardScore score = existingByCriterion.get(input.getCriterionId());
            if (score == null) {
                score = ScorecardScore.builder()
                        .id(UUID.randomUUID())
                        .submission(submission)
                        .criterion(criterion)
                        .createdAt(now)
                        .build();
            }
            score.setScore(input.getScore());
            score.setComment(input.getComment());
            score.setUpdatedAt(now);
            scorecardScoreRepository.save(score);
        }
    }

    /** BR-SCORE-01: every required criterion scored, and the overall comment non-blank (ME-29). */
    private void validateComplete(
            List<JobStageScorecardCriterion> criteria, Map<UUID, ScorecardScore> scoresByCriterion, String overallComment) {
        boolean missingRequiredScore = criteria.stream()
                .filter(JobStageScorecardCriterion::isRequired)
                .anyMatch(c -> scoresByCriterion.get(c.getId()) == null
                        || scoresByCriterion.get(c.getId()).getScore() == null);
        boolean commentBlank = overallComment == null || overallComment.isBlank();
        if (missingRequiredScore || commentBlank) {
            throw new BadRequestException(ErrorCode.SCORECARD_SUBMISSION_INCOMPLETE);
        }
    }

    /**
     * BR-SCORE-02: Sum(score x weight) / Sum(weight) - over only the
     * criteria that actually received a score. An optional (non-required)
     * criterion left unscored is excluded from BOTH sides of the fraction
     * rather than counted as 0, so a criterion nobody rated never drags the
     * average down for something that was never evaluated.
     */
    private BigDecimal computeWeightedScore(List<JobStageScorecardCriterion> criteria, Map<UUID, ScorecardScore> scoresByCriterion) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (JobStageScorecardCriterion criterion : criteria) {
            ScorecardScore score = scoresByCriterion.get(criterion.getId());
            if (score == null || score.getScore() == null) {
                continue;
            }
            weightedSum = weightedSum.add(score.getScore().multiply(criterion.getWeight()));
            totalWeight = totalWeight.add(criterion.getWeight());
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }

    /** Average of every {@code SUBMITTED} submission's weighted_score across the whole Application (UC-28 step 6). */
    private BigDecimal averageWeightedScore(List<ScorecardSubmission> submissions) {
        List<BigDecimal> submitted = submissions.stream()
                .filter(s -> s.getStatus() == ScorecardSubmissionStatus.SUBMITTED && s.getWeightedScore() != null)
                .map(ScorecardSubmission::getWeightedScore)
                .toList();
        if (submitted.isEmpty()) {
            return null;
        }
        BigDecimal sum = submitted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(submitted.size()), 2, RoundingMode.HALF_UP);
    }

    private void requireNotLocked(ScorecardSubmission submission) {
        if (submission.getLockedAt() != null) {
            throw new BusinessConflictException(ErrorCode.SCORECARD_SUBMISSION_LOCKED);
        }
    }

    private Map<UUID, ScorecardScore> scoresByCriterionId(UUID submissionId) {
        return scorecardScoreRepository.findBySubmission_Id(submissionId).stream()
                .collect(Collectors.toMap(s -> s.getCriterion().getId(), Function.identity()));
    }

    private ScorecardSubmissionResponseDto buildResponseDto(ScorecardSubmission submission) {
        List<JobStageScorecardCriterion> criteria = jobStageScorecardCriterionRepository
                .findByJobStageScorecard_IdOrderByPositionAsc(submission.getJobStageScorecard().getId());
        Map<UUID, ScorecardScore> scoresByCriterion = scoresByCriterionId(submission.getId());
        return ScorecardMapper.toResponseDto(submission, criteria, scoresByCriterion);
    }

    private Interview findInterviewOrThrow(UUID interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTERVIEW_NOT_FOUND, interviewId));
    }

    private ScorecardSubmission findSubmissionOrThrow(UUID submissionId) {
        return scorecardSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCORECARD_SUBMISSION_NOT_FOUND, submissionId));
    }
}
