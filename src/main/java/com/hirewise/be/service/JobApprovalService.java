package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.ApprovalDecision;
import com.hirewise.be.domain.JobApproval;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.response.JobApprovalDetailResponseDto;
import com.hirewise.be.dto.response.PendingApprovalJobSummaryResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobApprovalRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.repository.PipelineStageRepository;
import java.util.Collections;

/**
 * UC-14 + UC-15: Job approval workflow — list pending/historical approvals (UC-14),
 * view detail with Pipeline stages, and Approve/Reject decisions (UC-15).
 * <p>
 * Enforces BR-RBAC-01 (layer 2: JOB_APPROVE permission) and BR-APR-01
 * (layer 3: only jobs belonging to departments within the Hiring Manager's
 * access scope are visible / actionable).
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobApprovalService {

    JobPositionRepository jobPositionRepository;
    JobApprovalRepository jobApprovalRepository;
    UserAccessScopeRepository userAccessScopeRepository;
    UserRepository userRepository;
    DepartmentRepository departmentRepository;
    PipelineStageRepository pipelineStageRepository;
    AccessControlService accessControlService;
    OutboxEventPublisher outboxEventPublisher;
    Clock clock;

    // =========================================================================
    // UC-14: List approvals (with optional status filter)
    // =========================================================================

    /**
     * UC-14 normal flow: returns a paginated list of job positions visible
     * to the authenticated Hiring Manager, with an optional status filter.
     *
     * @param currentUser authenticated caller — must have {@code JOB_APPROVE}
     * @param status      optional status filter (null = all approval statuses)
     * @param pageable    pagination and sort (default: createdAt desc)
     * @return paginated list of approval job summaries
     */
    @Transactional(readOnly = true)
    public PagedResponseDto<PendingApprovalJobSummaryResponseDto> listPendingApproval(
            CurrentUser currentUser,
            JobStatus status,
            Pageable pageable) {

        // Layer 2: verify JOB_APPROVE permission (BR-RBAC-01)
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_APPROVE, ResourceContext.none());

        // Layer 3: resolve the Hiring Manager's department scope (BR-APR-01)
        Instant now = Instant.now(clock);
        List<UserAccessScope> activeScopes =
                userAccessScopeRepository.findActiveScopes(currentUser.userId(), now);

        boolean hasSystemScope = activeScopes.stream()
                .anyMatch(s -> s.getScopeType() == ScopeType.SYSTEM);

        Page<JobPosition> jobPage;
        if (hasSystemScope) {
            log.debug("UC-14: user {} has SYSTEM scope — querying jobs with status {}",
                    currentUser.userId(), status);
            jobPage = jobPositionRepository.findAllApprovalJobs(status, pageable);
        } else {
            List<Long> allowedDepartmentIds = resolveDepartmentIds(activeScopes);

            if (allowedDepartmentIds.isEmpty()) {
                log.debug("UC-14: user {} has no active department scopes — returning empty list",
                        currentUser.userId());
                return PagedResponseDto.<PendingApprovalJobSummaryResponseDto>builder()
                        .content(List.of())
                        .page(pageable.getPageNumber())
                        .size(pageable.getPageSize())
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build();
            }

            log.debug("UC-14: user {} querying approval jobs in {} department(s) with status {}",
                    currentUser.userId(), allowedDepartmentIds.size(), status);
            jobPage = jobPositionRepository
                    .findApprovalJobsInDepartments(allowedDepartmentIds, status, pageable);
        }

        List<PendingApprovalJobSummaryResponseDto> content = jobPage.getContent().stream()
                .map(this::toSummaryDto)
                .toList();

        return PagedResponseDto.from(jobPage, content);
    }

    // =========================================================================
    // UC-15: View detail + Approve / Reject
    // =========================================================================

    /**
     * UC-15 normal flow step 1: loads the full JD and pipeline stages for a single
     * job so the Hiring Manager can review it.
     *
     * @param jobId       UUID of the job position to review
     * @param currentUser authenticated caller — must have {@code JOB_APPROVE}
     *                    and be scoped to the job's department (BR-APR-01)
     * @return full JD detail DTO with pipeline stages
     */
    @Transactional(readOnly = true)
    public JobApprovalDetailResponseDto getJobDetail(UUID jobId, CurrentUser currentUser) {
        JobPosition job = loadJobForView(jobId, currentUser);
        return toDetailDto(job);
    }

    /**
     * UC-15 normal flow steps 2-3: Hiring Manager approves the job position.
     * <ul>
     *   <li>Sets {@code job_positions.status = APPROVED}.</li>
     *   <li>Appends a new {@link JobApproval} row with {@code decision = APPROVED}.</li>
     *   <li>Enqueues EM-03 email to the Recruiter via the transactional outbox.</li>
     * </ul>
     *
     * @param jobId       UUID of the job position to approve
     * @param currentUser authenticated Hiring Manager — must have {@code JOB_APPROVE}
     *                    and be within access scope (BR-APR-01)
     */
    @Transactional
    public void approveJob(UUID jobId, CurrentUser currentUser) {
        JobPosition job = loadJobForDecision(jobId, currentUser);
        Instant now = Instant.now(clock);

        // Update job status
        job.setStatus(JobStatus.APPROVED);
        job.setUpdatedAt(now);
        jobPositionRepository.save(job);

        // Append approval trail (BR-APR-01 audit)
        User decidedByUser = userRepository.getReferenceById(currentUser.userId());
        JobApproval approval = JobApproval.builder()
                .jobPosition(job)
                .decision(ApprovalDecision.APPROVED)
                .decidedBy(decidedByUser)
                .decidedAt(now)
                .createdAt(now)
                .build();
        jobApprovalRepository.save(approval);

        // EM-03: notify Recruiter (async via outbox — never blocks the HTTP response)
        enqueueApprovalEmail(job, true, null);

        log.info("UC-15: job {} APPROVED by user {}", jobId, currentUser.userId());
    }

    /**
     * UC-15 AF-01: Hiring Manager rejects the job position with a mandatory reason.
     * <ul>
     *   <li>Sets {@code job_positions.status = REJECTED} (Recruiter can re-edit).</li>
     *   <li>Appends a new {@link JobApproval} row with {@code decision = REJECTED}
     *       and the reason text (BR-APR-02).</li>
     *   <li>Enqueues EM-03 email to the Recruiter kèm lý do.</li>
     * </ul>
     *
     * @param jobId       UUID of the job position to reject
     * @param reason      mandatory rejection reason (BR-APR-02, >= 10 chars)
     * @param currentUser authenticated Hiring Manager — must have {@code JOB_APPROVE}
     *                    and be within access scope (BR-APR-01)
     */
    @Transactional
    public void rejectJob(UUID jobId, String reason, CurrentUser currentUser) {
        JobPosition job = loadJobForDecision(jobId, currentUser);
        Instant now = Instant.now(clock);

        // Update job status — job goes back to Recruiter for edits (BR-APR-02)
        job.setStatus(JobStatus.REJECTED);
        job.setUpdatedAt(now);
        jobPositionRepository.save(job);

        // Append approval trail with reason
        User decidedByUser = userRepository.getReferenceById(currentUser.userId());
        JobApproval approval = JobApproval.builder()
                .jobPosition(job)
                .decision(ApprovalDecision.REJECTED)
                .reason(reason)
                .decidedBy(decidedByUser)
                .decidedAt(now)
                .createdAt(now)
                .build();
        jobApprovalRepository.save(approval);

        // EM-03: notify Recruiter with rejection reason
        enqueueApprovalEmail(job, false, reason);

        log.info("UC-15: job {} REJECTED by user {} — reason: {}", jobId, currentUser.userId(), reason);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads a job position for viewing detail. Allows viewing jobs in any state as
     * long as the caller has {@code JOB_APPROVE} scoped to its department.
     */
    private JobPosition loadJobForView(UUID jobId, CurrentUser currentUser) {
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND));

        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_APPROVE,
                ResourceContext.job(jobId, departmentId));

        return job;
    }

    /**
     * Loads a job position for making a decision (approve/reject). Enforces that the
     * job is currently in {@code PENDING_APPROVAL} status.
     */
    private JobPosition loadJobForDecision(UUID jobId, CurrentUser currentUser) {
        JobPosition job = loadJobForView(jobId, currentUser);

        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw new BadRequestException(ErrorCode.JOB_POSITION_ALREADY_CLOSED,
                    "Job position is not in PENDING_APPROVAL status.");
        }

        return job;
    }

    /**
     * Enqueues an EM-03 outbox event to notify the Recruiter of the decision.
     */
    private void enqueueApprovalEmail(JobPosition job, boolean approved, String reason) {
        User recruiter = job.getRecruiter();
        if (recruiter == null || recruiter.getEmail() == null) {
            log.warn("UC-15: job {} has no recruiter email — skipping EM-03 notification", job.getId());
            return;
        }
        outboxEventPublisher.publish(
                OutboxEventType.JOB_APPROVAL_DECISION_EMAIL,
                OutboxPayloads.jobApprovalDecisionEmail(
                        recruiter.getEmail(),
                        recruiter.getFullName(),
                        job.getTitle(),
                        approved,
                        reason));
    }

    /**
     * Expands each active DEPARTMENT scope into the full set of department ids
     * the manager is allowed to see (self + descendants, BR-RBAC-06).
     */
    private List<Long> resolveDepartmentIds(List<UserAccessScope> activeScopes) {
        List<Long> result = new ArrayList<>();
        for (UserAccessScope scope : activeScopes) {
            if (scope.getScopeType() != ScopeType.DEPARTMENT
                    || scope.getDepartment() == null) {
                continue;
            }
            Long rootId = scope.getDepartment().getId();
            if (scope.isIncludeSubDepartments()) {
                List<Long> ids = departmentRepository.findSelfAndDescendantIds(rootId);
                result.addAll(ids);
            } else {
                result.add(rootId);
            }
        }
        return result;
    }

    /**
     * Maps a {@link JobPosition} to the list-row summary DTO (UC-14).
     */
    private PendingApprovalJobSummaryResponseDto toSummaryDto(JobPosition job) {
        String departmentName = job.getDepartment() != null ? job.getDepartment().getName() : null;
        String createdByUserName = job.getRecruiter() != null ? job.getRecruiter().getFullName() : null;
        String pipelineName = job.getPipelineTemplate() != null ? job.getPipelineTemplate().getName() : null;

        return PendingApprovalJobSummaryResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .departmentName(departmentName)
                .openings(job.getOpenings())
                .employmentType(job.getEmploymentType())
                .createdByUserName(createdByUserName)
                .submittedAt(job.getUpdatedAt())
                .status(job.getStatus())
                .pipelineTemplateName(pipelineName)
                .build();
    }

    /**
     * Maps a {@link JobPosition} to the full detail DTO (UC-15 step 1),
     * including attached Pipeline Template stages.
     */
    private JobApprovalDetailResponseDto toDetailDto(JobPosition job) {
        String departmentName = job.getDepartment() != null ? job.getDepartment().getName() : null;
        String createdByUserName = job.getRecruiter() != null ? job.getRecruiter().getFullName() : null;

        Long pipelineTemplateId = null;
        String pipelineTemplateName = null;
        List<PipelineStageResponseDto> pipelineStages = Collections.emptyList();

        if (job.getPipelineTemplate() != null) {
            pipelineTemplateId = job.getPipelineTemplate().getId();
            pipelineTemplateName = job.getPipelineTemplate().getName();
            pipelineStages = pipelineStageRepository
                    .findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(pipelineTemplateId)
                    .stream()
                    .map(stage -> PipelineStageResponseDto.builder()
                            .id(stage.getId())
                            .pipelineTemplateId(stage.getPipelineTemplate().getId())
                            .name(stage.getName())
                            .code(stage.getCode())
                            .stageType(stage.getStageType())
                            .position(stage.getPosition())
                            .terminal(stage.isTerminal())
                            .slaHours(stage.getSlaHours())
                            .active(stage.isActive())
                            .createdAt(stage.getCreatedAt())
                            .updatedAt(stage.getUpdatedAt())
                            .pipelineTemplateName(stage.getPipelineTemplate().getName())
                            .build())
                    .toList();
        }

        return JobApprovalDetailResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .departmentName(departmentName)
                .openings(job.getOpenings())
                .employmentType(job.getEmploymentType())
                .location(job.getLocation())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .applicationDeadline(job.getApplicationDeadline())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .benefits(job.getBenefits())
                .createdByUserName(createdByUserName)
                .submittedAt(job.getUpdatedAt())
                .status(job.getStatus())
                .pipelineTemplateId(pipelineTemplateId)
                .pipelineTemplateName(pipelineTemplateName)
                .pipelineStages(pipelineStages)
                .build();
    }
}


