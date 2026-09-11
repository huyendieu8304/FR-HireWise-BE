package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStageScorecard;
import com.hirewise.be.domain.JobStageScorecardCriterion;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.ScorecardCriterion;
import com.hirewise.be.domain.ScorecardStatus;
import com.hirewise.be.domain.ScorecardTemplate;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.ScorecardCriterionInputDto;
import com.hirewise.be.dto.request.SaveJobStageScorecardRequestDto;
import com.hirewise.be.dto.response.InterviewStageScorecardStatusDto;
import com.hirewise.be.dto.response.JobStageScorecardResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.ScorecardMapper;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.JobStageScorecardCriterionRepository;
import com.hirewise.be.repository.JobStageScorecardRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.ScorecardCriterionRepository;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
import com.hirewise.be.repository.ScorecardTemplateRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-27 step 3 + UC-14/15 hard gate: the REAL per-(Job, Stage) Scorecard
 * scoring definition that Hiring Managers configure - either by cloning 1
 * {@link ScorecardTemplate} (Master library) as a starting point and
 * customizing it, or from scratch. Every {@code INTERVIEW}-type Stage of a
 * Job's pipeline must have one configured before the Job can be Approved
 * (see {@code JobApprovalService#approveJob}), and it can be edited again at
 * any time afterwards - see {@link JobStageScorecard}'s own Javadoc for the
 * AF-01 versioning rule that governs edits made after it already has
 * grading history.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobStageScorecardService {

    JobStageScorecardRepository jobStageScorecardRepository;
    JobStageScorecardCriterionRepository jobStageScorecardCriterionRepository;
    ScorecardSubmissionRepository scorecardSubmissionRepository;
    ScorecardTemplateRepository scorecardTemplateRepository;
    JobPositionRepository jobPositionRepository;
    PipelineStageRepository pipelineStageRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * @param jobId          id of the Job
     * @param pipelineStageId id of the ({@code INTERVIEW}-type) Stage
     * @param currentUser    authenticated caller, must have {@code SCORECARD_TEMPLATE_MANAGE}
     *                       scoped to the Job's department
     * @return the current version of the Scorecard configured for this (Job, Stage) pair
     * @throws ResourceNotFoundException if the Job/Stage don't exist, or no Scorecard is
     *                                    configured for this pair yet
     */
    public JobStageScorecardResponseDto getForJobStage(UUID jobId, Long pipelineStageId, CurrentUser currentUser) {
        JobPosition job = findJobOrThrow(jobId);
        checkManageAccess(currentUser, job);

        JobStageScorecard scorecard = jobStageScorecardRepository
                .findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(jobId, pipelineStageId, ScorecardStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_STAGE_SCORECARD_NOT_FOUND, pipelineStageId));
        return toResponseDtoWithCriteria(scorecard);
    }

    /**
     * UC-27 step 3 + AF-01: creates the Scorecard for this (Job, Stage) pair
     * if none exists yet, edits the current version in place if it has never
     * been graded against, or archives it and creates a new version if it
     * already has >= 1 {@link com.hirewise.be.domain.ScorecardSubmission}
     * referencing it - same 3-way rule {@code ScorecardTemplateService} used
     * to apply at the (now retired) per-Job template level.
     *
     * @param jobId           id of the Job
     * @param pipelineStageId id of the Stage - must be an {@code INTERVIEW}-type
     *                        Stage that actually belongs to the Job's pipeline
     * @param request         name, optional source Master Template id (traceability only),
     *                        and the full criteria list
     * @param currentUser     Hiring Manager/HR Admin performing the save
     * @return the resulting (Job, Stage) Scorecard
     * @throws ResourceNotFoundException if the Job/Stage/source Master Template don't exist
     * @throws BadRequestException       if the Stage doesn't belong to this Job's pipeline,
     *                                    isn't an {@code INTERVIEW}-type Stage, or the total
     *                                    weight of all criteria is 0 (EX-01)
     */
    @Transactional
    public JobStageScorecardResponseDto saveForJobStage(
            UUID jobId, Long pipelineStageId, SaveJobStageScorecardRequestDto request, CurrentUser currentUser) {
        JobPosition job = findJobOrThrow(jobId);
        checkManageAccess(currentUser, job);
        PipelineStage stage = findInterviewStageOfJobOrThrow(job, pipelineStageId);
        ScorecardTemplateService.validateTotalWeight(request.getCriteria());

        ScorecardTemplate sourceMaster = null;
        if (request.getSourceMasterTemplateId() != null) {
            sourceMaster = scorecardTemplateRepository.findById(request.getSourceMasterTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.SCORECARD_TEMPLATE_NOT_FOUND, request.getSourceMasterTemplateId()));
        }

        Instant now = Instant.now(clock);
        var existing = jobStageScorecardRepository
                .findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(jobId, pipelineStageId, ScorecardStatus.ACTIVE);

        if (existing.isEmpty()) {
            JobStageScorecard created = JobStageScorecard.builder()
                    .id(UUID.randomUUID())
                    .job(job)
                    .pipelineStage(stage)
                    .name(request.getName())
                    .version(1)
                    .status(ScorecardStatus.ACTIVE)
                    .sourceMasterTemplate(sourceMaster)
                    .createdBy(User.builder().id(currentUser.userId()).build())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            jobStageScorecardRepository.save(created);
            List<JobStageScorecardCriterion> criteria = buildCriteria(created, request.getCriteria());
            jobStageScorecardCriterionRepository.saveAll(criteria);

            log.info("Created scorecard for (job={}, stage={}): {}", jobId, pipelineStageId, created.getId());
            return ScorecardMapper.toResponseDto(created, criteria);
        }

        JobStageScorecard current = existing.get();
        boolean alreadyGraded = scorecardSubmissionRepository.existsByJobStageScorecard_Id(current.getId());
        if (!alreadyGraded) {
            current.setName(request.getName());
            current.setSourceMasterTemplate(sourceMaster);
            current.setUpdatedAt(now);
            jobStageScorecardRepository.save(current);

            jobStageScorecardCriterionRepository.deleteByJobStageScorecard_Id(current.getId());
            List<JobStageScorecardCriterion> criteria = buildCriteria(current, request.getCriteria());
            jobStageScorecardCriterionRepository.saveAll(criteria);

            log.info("Edited scorecard in place for (job={}, stage={}): {} (never graded yet)",
                    jobId, pipelineStageId, current.getId());
            return ScorecardMapper.toResponseDto(current, criteria);
        }

        current.setStatus(ScorecardStatus.ARCHIVED);
        current.setUpdatedAt(now);
        jobStageScorecardRepository.save(current);

        JobStageScorecard newVersion = JobStageScorecard.builder()
                .id(UUID.randomUUID())
                .job(job)
                .pipelineStage(stage)
                .name(request.getName())
                .version(current.getVersion() + 1)
                .status(ScorecardStatus.ACTIVE)
                .sourceMasterTemplate(sourceMaster)
                .createdBy(User.builder().id(currentUser.userId()).build())
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobStageScorecardRepository.save(newVersion);
        List<JobStageScorecardCriterion> criteria = buildCriteria(newVersion, request.getCriteria());
        jobStageScorecardCriterionRepository.saveAll(criteria);

        log.info("Versioned scorecard for (job={}, stage={}): {} -> {} (v{} -> v{}, old one already graded)",
                jobId, pipelineStageId, current.getId(), newVersion.getId(), current.getVersion(), newVersion.getVersion());
        return ScorecardMapper.toResponseDto(newVersion, criteria);
    }

    /**
     * UC-14/15 hard gate + UC-28 step 6 stage labeling: 1 row per
     * {@code INTERVIEW}-type Stage of the Job's pipeline, saying whether it
     * already has an {@code ACTIVE} Scorecard configured. Loaded via a
     * single query (all ACTIVE scorecards of the job) rather than 1 query
     * per Stage.
     *
     * @param job the Job Position (its pipeline template must already be loaded/assigned)
     * @return 1 status row per Interview-type Stage, in pipeline order; empty if the
     *         Job has no pipeline template assigned, or its pipeline has no such Stage
     */
    public List<InterviewStageScorecardStatusDto> getStageStatusForJob(JobPosition job) {
        if (job.getPipelineTemplate() == null) {
            return List.of();
        }
        List<PipelineStage> interviewStages = pipelineStageRepository
                .findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(job.getPipelineTemplate().getId())
                .stream()
                .filter(s -> s.getStageType() == StageType.INTERVIEW)
                .toList();
        if (interviewStages.isEmpty()) {
            return List.of();
        }

        Map<Long, JobStageScorecard> configuredByStageId = jobStageScorecardRepository
                .findByJob_IdAndStatus(job.getId(), ScorecardStatus.ACTIVE).stream()
                .collect(Collectors.toMap(jss -> jss.getPipelineStage().getId(), jss -> jss));

        return interviewStages.stream()
                .map(stage -> {
                    JobStageScorecard configured = configuredByStageId.get(stage.getId());
                    return InterviewStageScorecardStatusDto.builder()
                            .pipelineStageId(stage.getId())
                            .stageName(stage.getName())
                            .position(stage.getPosition())
                            .configured(configured != null)
                            .jobStageScorecardId(configured != null ? configured.getId() : null)
                            .build();
                })
                .toList();
    }

    private void checkManageAccess(CurrentUser currentUser, JobPosition job) {
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_TEMPLATE_MANAGE,
                ResourceContext.job(job.getId(), departmentId));
    }

    private PipelineStage findInterviewStageOfJobOrThrow(JobPosition job, Long pipelineStageId) {
        PipelineStage stage = pipelineStageRepository.findById(pipelineStageId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIPELINE_STAGE_NOT_FOUND, pipelineStageId));
        if (job.getPipelineTemplate() == null
                || !stage.getPipelineTemplate().getId().equals(job.getPipelineTemplate().getId())) {
            throw new BadRequestException(ErrorCode.INVALID_STAGE_TRANSITION);
        }
        if (stage.getStageType() != StageType.INTERVIEW) {
            throw new BadRequestException(ErrorCode.SCORECARD_STAGE_NOT_INTERVIEW_TYPE);
        }
        return stage;
    }

    /** Same indexed-loop rationale as {@code ScorecardTemplateService#buildCriteria}. */
    private List<JobStageScorecardCriterion> buildCriteria(JobStageScorecard scorecard, List<ScorecardCriterionInputDto> inputs) {
        List<JobStageScorecardCriterion> criteria = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            ScorecardCriterionInputDto input = inputs.get(index);
            criteria.add(JobStageScorecardCriterion.builder()
                    .id(UUID.randomUUID())
                    .jobStageScorecard(scorecard)
                    .name(input.getName())
                    .description(input.getDescription())
                    .weight(input.getWeight())
                    .maxScore(input.getMaxScore())
                    .position(index + 1)
                    .required(input.isRequired())
                    .build());
        }
        return criteria;
    }

    private JobPosition findJobOrThrow(UUID jobId) {
        return jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));
    }

    private JobStageScorecardResponseDto toResponseDtoWithCriteria(JobStageScorecard scorecard) {
        List<JobStageScorecardCriterion> criteria = jobStageScorecardCriterionRepository
                .findByJobStageScorecard_IdOrderByPositionAsc(scorecard.getId());
        return ScorecardMapper.toResponseDto(scorecard, criteria);
    }
}
