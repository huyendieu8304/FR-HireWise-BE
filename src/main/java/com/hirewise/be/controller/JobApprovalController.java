package com.hirewise.be.controller;

import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.response.PendingApprovalJobSummaryResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.JobApprovalService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-14: Hiring Manager views job positions pending approval.
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET /api/job-approvals/pending} — {@code JOB_APPROVE};
 *       results are further scoped to departments the manager is responsible
 *       for (BR-APR-01, BR-RBAC-01) — enforced inside
 *       {@link JobApprovalService#listPendingApproval}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/job-approvals")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobApprovalController {

    JobApprovalService jobApprovalService;

    /**
     * UC-14 normal flow steps 2-3: returns the paginated list of
     * {@code PENDING_APPROVAL} job positions visible to the authenticated
     * Hiring Manager.
     *
     * <p>EX-01: when no jobs are pending within the manager's scope, the
     * response body is a valid {@code PagedResponseDto} with an empty
     * {@code content} array and {@code totalElements = 0} — the UI is
     * responsible for showing the "Không có yêu cầu nào đang chờ duyệt"
     * empty state message.
     *
     * @param page        zero-based page index (default 0)
     * @param size        page size (default 20)
     * @param currentUser authenticated Hiring Manager, must have {@code JOB_APPROVE}
     * @return 200 OK with paginated list, or 403 if caller lacks {@code JOB_APPROVE}
     */
    @GetMapping("/pending")
    public ResponseEntity<PagedResponseDto<PendingApprovalJobSummaryResponseDto>> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserPrincipal CurrentUser currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PagedResponseDto<PendingApprovalJobSummaryResponseDto> result =
                jobApprovalService.listPendingApproval(currentUser, pageable);
        return ResponseEntity.ok(result);
    }
}
