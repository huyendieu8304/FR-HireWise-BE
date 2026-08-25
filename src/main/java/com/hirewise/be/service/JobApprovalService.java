package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.response.PendingApprovalJobSummaryResponseDto;
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

/**
 * UC-14: Xem danh sách yêu cầu tuyển dụng đang chờ duyệt.
 * <p>
 * Enforces BR-RBAC-01 (layer 2: JOB_APPROVE permission) and BR-APR-01
 * (layer 3: only jobs belonging to departments within the Hiring Manager's
 * access scope are returned).
 * <p>
 * RBAC resolution flow:
 * <ol>
 *   <li>Layer 2 — {@code AccessControlService.checkAccess(user, JOB_APPROVE, null)}: throws
 *       {@code PermissionDeniedException} if the user's roles don't include
 *       {@code JOB_APPROVE}.</li>
 *   <li>Layer 3 — resolve active department ids from {@code user_access_scopes}:
 *       <ul>
 *         <li>SYSTEM scope → see all PENDING_APPROVAL jobs (admin view).</li>
 *         <li>DEPARTMENT scope → expand with sub-departments via recursive CTE
 *             (BR-RBAC-06) and filter by the resulting id list.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobApprovalService {

    JobPositionRepository jobPositionRepository;
    UserAccessScopeRepository userAccessScopeRepository;
    DepartmentRepository departmentRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-14 normal flow: returns a paginated list of job positions that are
     * {@code PENDING_APPROVAL} and fall within the authenticated Hiring
     * Manager's department scope.
     *
     * @param currentUser authenticated caller — must have {@code JOB_APPROVE}
     * @param pageable    pagination and sort (default: createdAt desc)
     * @return paginated list of pending-approval job summaries
     */
    @Transactional(readOnly = true)
    public PagedResponseDto<PendingApprovalJobSummaryResponseDto> listPendingApproval(
            CurrentUser currentUser,
            Pageable pageable) {

        // Layer 2: verify JOB_APPROVE permission (BR-RBAC-01)
        // ResourceContext.none() — the permission itself is scope-independent;
        // the scope filtering is applied below at the repository query level.
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_APPROVE, ResourceContext.none());

        // Layer 3: resolve the Hiring Manager's department scope (BR-APR-01)
        Instant now = Instant.now(clock);
        List<UserAccessScope> activeScopes =
                userAccessScopeRepository.findActiveScopes(currentUser.userId(), now);

        // Check for SYSTEM scope first — if present, the manager can see everything
        boolean hasSystemScope = activeScopes.stream()
                .anyMatch(s -> s.getScopeType() == ScopeType.SYSTEM);

        Page<JobPosition> jobPage;
        if (hasSystemScope) {
            log.debug("UC-14: user {} has SYSTEM scope — returning all PENDING_APPROVAL jobs",
                    currentUser.userId());
            jobPage = jobPositionRepository.findAllPendingApproval(pageable);
        } else {
            // Collect all allowed department ids (with sub-departments) across
            // every active DEPARTMENT scope (BR-RBAC-05: union of scopes).
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

            log.debug("UC-14: user {} querying PENDING_APPROVAL in {} department(s)",
                    currentUser.userId(), allowedDepartmentIds.size());
            jobPage = jobPositionRepository
                    .findPendingApprovalInDepartments(allowedDepartmentIds, pageable);
        }

        List<PendingApprovalJobSummaryResponseDto> content = jobPage.getContent().stream()
                .map(this::toSummaryDto)
                .toList();

        return PagedResponseDto.from(jobPage, content);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

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
                // BR-RBAC-06: recursive CTE fetches self + all descendants
                List<Long> ids = departmentRepository.findSelfAndDescendantIds(rootId);
                result.addAll(ids);
            } else {
                result.add(rootId);
            }
        }
        return result;
    }

    /**
     * Maps a {@link JobPosition} (with its department and recruiter already
     * join-fetched by the repository query) to the list-row DTO.
     */
    private PendingApprovalJobSummaryResponseDto toSummaryDto(JobPosition job) {
        String departmentName = job.getDepartment() != null
                ? job.getDepartment().getName()
                : null;

        // Recruiter is the "Người tạo" (creator) shown in the UC-14 list.
        // Falls back to null gracefully when the recruiter account was deleted.
        String createdByUserName = job.getRecruiter() != null
                ? job.getRecruiter().getFullName()
                : null;

        return PendingApprovalJobSummaryResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .departmentName(departmentName)
                .openings(job.getOpenings())
                .employmentType(job.getEmploymentType())
                .createdByUserName(createdByUserName)
                .submittedAt(job.getUpdatedAt())
                .build();
    }
}
