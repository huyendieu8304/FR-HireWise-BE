package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.JobPositionRequestDto;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.dto.response.JobSummaryResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.JobMapper;
import com.hirewise.be.repository.DepartmentRepository;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Vị trí tuyển dụng" - the internal Job Position list/detail screen (sidebar
 * entry point into a Job's JD + Kanban board), gated by {@code JOB_VIEW}
 * (granted to HR_ADMIN, RECRUITER and HIRING_MANAGER - see RBAC.md) rather
 * than {@code JOB_APPROVE} like {@link JobApprovalService}, so a Recruiter
 * (who never has {@code JOB_APPROVE}) can browse and open their own jobs
 * too. Scope resolution (SYSTEM vs DEPARTMENT Access Scope) mirrors UC-14's
 * {@code JobApprovalService#listPendingApproval} - kept as its own copy
 * here rather than a shared helper, matching this codebase's existing
 * one-service-per-screen convention.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobService {

    JobPositionRepository jobPositionRepository;
    UserAccessScopeRepository userAccessScopeRepository;
    DepartmentRepository departmentRepository;
    UserRepository userRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * Lists every Job Position visible to the caller (any status), with
     * optional department/status/keyword filters from the UI — the keyword
     * filter backs the "Vị trí tuyển dụng" search box (case-insensitive
     * substring match on the job title), shared by HR Admin, Recruiter,
     * Hiring Manager and (since V26) Interviewer.
     *
     * @param currentUser  authenticated caller, must have {@code JOB_VIEW}
     * @param departmentId optional department filter
     * @param status       optional status filter
     * @param keyword      optional search box text — matched against the job title
     * @param pageable     pagination and sort (default: createdAt desc)
     * @return paginated list of job summaries within the caller's Access Scope
     */
    @Transactional(readOnly = true)
    public PagedResponseDto<JobSummaryResponseDto> listJobs(
            CurrentUser currentUser, Long departmentId, JobStatus status, String keyword, Pageable pageable) {

        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_VIEW, ResourceContext.none());

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();

        Instant now = Instant.now(clock);
        List<UserAccessScope> activeScopes = userAccessScopeRepository.findActiveScopes(currentUser.userId(), now);
        boolean hasSystemScope = activeScopes.stream().anyMatch(s -> s.getScopeType() == ScopeType.SYSTEM);

        Page<JobPosition> jobPage;
        if (hasSystemScope) {
            jobPage = jobPositionRepository.searchAllJobs(departmentId, status, normalizedKeyword, pageable);
        } else {
            List<Long> allowedDepartmentIds = resolveDepartmentIds(activeScopes);
            if (allowedDepartmentIds.isEmpty()) {
                log.debug("Vị trí tuyển dụng: user {} has no active department scope — returning empty list",
                        currentUser.userId());
                return PagedResponseDto.<JobSummaryResponseDto>builder()
                        .content(List.of())
                        .page(pageable.getPageNumber())
                        .size(pageable.getPageSize())
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build();
            }
            jobPage = jobPositionRepository.searchJobsInDepartments(
                    allowedDepartmentIds, departmentId, status, normalizedKeyword, pageable);
        }

        List<JobSummaryResponseDto> content = jobPage.getContent().stream()
                .map(JobMapper::toSummaryDto)
                .toList();
        return PagedResponseDto.from(jobPage, content);
    }

    /**
     * Full JD detail for one Job Position (tab "Mô tả chi tiết").
     *
     * @param jobId       id of the job position
     * @param currentUser authenticated caller, must have {@code JOB_VIEW}
     *                    scoped to the job's department
     * @return the job's full detail
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     */
    @Transactional(readOnly = true)
    public JobDetailResponseDto getJobDetail(UUID jobId, CurrentUser currentUser) {
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));

        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_VIEW, ResourceContext.job(jobId, departmentId));

        return JobMapper.toDetailDto(job);
    }

    /**
     * UC-12 normal flow: creates a new Job Position in {@code DRAFT} status,
     * self-assigned to the calling Recruiter. Only the fields the Screen
     * Description marks "Bắt buộc" for saving a Draft are enforced here
     * (title/department/openings, Bean Validation on the DTO) plus the two
     * cross-field rules that apply to the field itself regardless of
     * draft-vs-submit (BR-JOB-02 salary range, BR-JOB-03 deadline) - the
     * fuller BR-JOB-01 checklist (+ employment type + Pipeline Template) is
     * only enforced when submitting for approval (UC-13), not here.
     *
     * @param request     JD fields for the new Draft
     * @param currentUser Recruiter performing the creation; becomes the job's owner
     * @return the created job
     * @throws ResourceNotFoundException if {@code departmentId} does not exist
     * @throws BadRequestException       if the salary range or deadline is invalid (EX-02/EX-03)
     */
    @Transactional
    public JobDetailResponseDto createJob(JobPositionRequestDto request, CurrentUser currentUser) {
        // Layer 3 needs the target department up front for a create action - there is no
        // existing row yet to load it from (same reasoning as PipelineService#createTemplate).
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_CREATE,
                ResourceContext.department(request.getDepartmentId()));

        Department department = findDepartmentOrThrow(request.getDepartmentId());
        validateSalaryRange(request.getSalaryMin(), request.getSalaryMax());
        validateDeadlineInFuture(request.getApplicationDeadline());

        Instant now = Instant.now(clock);
        User recruiter = userRepository.getReferenceById(currentUser.userId());

        JobPosition job = JobPosition.builder()
                .id(UUID.randomUUID())
                .title(request.getTitle())
                .department(department)
                .location(request.getLocation())
                .employmentType(request.getEmploymentType())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .openings(request.getOpenings())
                .applicationDeadline(request.getApplicationDeadline())
                .description(request.getDescription())
                .requirements(request.getRequirements())
                .benefits(request.getBenefits())
                .status(JobStatus.DRAFT)
                .createdByUserId(currentUser.userId())
                .recruiter(recruiter)
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobPositionRepository.save(job);

        log.info("Created job position: {} (title={}, departmentId={})",
                job.getId(), job.getTitle(), department.getId());
        return JobMapper.toDetailDto(job);
    }

    /**
     * UC-12 AF-01: re-saves a Draft (or Rejected) Job Position's fields -
     * "Lưu nháp" can be repeated any number of times before submitting for
     * approval (UC-13). BR-JOB-04 also blocks editing once a job has moved
     * past Draft/Rejected (e.g. Published can only be Closed/Paused, not
     * edited here).
     *
     * @param jobId       id of the job position to update
     * @param request     the full JD form, resent as-is (not a partial patch)
     * @param currentUser Recruiter (or anyone else granted {@code JOB_EDIT}
     *                    within the job's department) performing the edit
     * @return the updated job
     * @throws ResourceNotFoundException if no job exists with {@code jobId}, or if
     *                                    {@code departmentId} does not exist
     * @throws BusinessConflictException if the job is no longer in {@code DRAFT}/{@code REJECTED} status
     * @throws BadRequestException       if the salary range or deadline is invalid (EX-02/EX-03)
     */
    @Transactional
    public JobDetailResponseDto updateDraftJob(UUID jobId, JobPositionRequestDto request, CurrentUser currentUser) {
        // The job must be loaded first to know which department scope (Layer 3) applies,
        // and which status it is currently in (BR-JOB-04) - both come from the existing row.
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));
        Long currentDepartmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_EDIT,
                ResourceContext.department(currentDepartmentId));

        // BR-JOB-04: only Draft/Rejected jobs can still be edited here - a Published job is
        // only Closed/Paused (a different action), never edited back through this endpoint.
        if (job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.REJECTED) {
            throw new BusinessConflictException(ErrorCode.JOB_POSITION_NOT_EDITABLE, job.getStatus());
        }

        Department department = findDepartmentOrThrow(request.getDepartmentId());
        validateSalaryRange(request.getSalaryMin(), request.getSalaryMax());
        validateDeadlineInFuture(request.getApplicationDeadline());

        job.setTitle(request.getTitle());
        job.setDepartment(department);
        job.setLocation(request.getLocation());
        job.setEmploymentType(request.getEmploymentType());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setOpenings(request.getOpenings());
        job.setApplicationDeadline(request.getApplicationDeadline());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setBenefits(request.getBenefits());
        job.setUpdatedAt(Instant.now(clock));
        jobPositionRepository.save(job);

        log.info("Updated draft job position: {} (title={}, status={})", job.getId(), job.getTitle(), job.getStatus());
        return JobMapper.toDetailDto(job);
    }

    /** BR-JOB-02: salary_min must not exceed salary_max when both are provided (EX-02/ME-19). */
    private static void validateSalaryRange(BigDecimal salaryMin, BigDecimal salaryMax) {
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw new BadRequestException(ErrorCode.JOB_SALARY_RANGE_INVALID);
        }
    }

    /** BR-JOB-03: application deadline, when set, must be a future date (EX-03/ME-20). */
    private void validateDeadlineInFuture(LocalDate applicationDeadline) {
        if (applicationDeadline != null && !applicationDeadline.isAfter(LocalDate.now(clock))) {
            throw new BadRequestException(ErrorCode.JOB_DEADLINE_IN_PAST);
        }
    }

    private Department findDepartmentOrThrow(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND, departmentId));
    }

    /** Same DEPARTMENT-scope resolution as {@code JobApprovalService#resolveDepartmentIds}. */
    private List<Long> resolveDepartmentIds(List<UserAccessScope> activeScopes) {
        List<Long> result = new ArrayList<>();
        for (UserAccessScope scope : activeScopes) {
            if (scope.getScopeType() != ScopeType.DEPARTMENT || scope.getDepartment() == null) {
                continue;
            }
            Long rootId = scope.getDepartment().getId();
            if (scope.isIncludeSubDepartments()) {
                result.addAll(departmentRepository.findSelfAndDescendantIds(rootId));
            } else {
                result.add(rootId);
            }
        }
        return result;
    }
}
