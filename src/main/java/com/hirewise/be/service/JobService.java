package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.dto.response.JobSummaryResponseDto;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.JobMapper;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
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
