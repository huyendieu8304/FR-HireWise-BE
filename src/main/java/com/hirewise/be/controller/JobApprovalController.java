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

import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.request.RejectJobRequestDto;
import com.hirewise.be.dto.response.JobApprovalDetailResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

/**
 * UC-14 + UC-15: Hiring Manager job approval controller.
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET /api/job-approvals/pending} — {@code JOB_APPROVE}; scoped to departments</li>
 *   <li>{@code GET /api/job-approvals/{jobId}} — {@code JOB_APPROVE}; scoped to job's department</li>
 *   <li>{@code POST /api/job-approvals/{jobId}/approve} — {@code JOB_APPROVE}; scoped to job's department</li>
 *   <li>{@code POST /api/job-approvals/{jobId}/reject} — {@code JOB_APPROVE}; scoped to job's department</li>
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
     * job positions visible to the authenticated Hiring Manager,
     * with an optional status filter.
     */
    @GetMapping("/pending")
    public ResponseEntity<PagedResponseDto<PendingApprovalJobSummaryResponseDto>> listPending(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserPrincipal CurrentUser currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PagedResponseDto<PendingApprovalJobSummaryResponseDto> result =
                jobApprovalService.listPendingApproval(currentUser, status, pageable);
        return ResponseEntity.ok(result);
    }


    /**
     * UC-15 normal flow step 1: Hiring Manager views the full details of a single
     * job position pending approval.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobApprovalDetailResponseDto> getDetail(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {

        JobApprovalDetailResponseDto result = jobApprovalService.getJobDetail(jobId, currentUser);
        return ResponseEntity.ok(result);
    }

    /**
     * UC-15 normal flow steps 2-3: Hiring Manager approves a job position.
     * Transitions status to APPROVED and sends notification email to Recruiter (EM-03).
     */
    @PostMapping("/{jobId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {

        jobApprovalService.approveJob(jobId, currentUser);
    }

    /**
     * UC-15 AF-01: Hiring Manager rejects a job position with a mandatory reason.
     * Transitions status to REJECTED (for recruiter re-editing) and sends rejection email (EM-03).
     */
    @PostMapping("/{jobId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            @PathVariable UUID jobId,
            @Valid @RequestBody RejectJobRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {

        jobApprovalService.rejectJob(jobId, request.getReason(), currentUser);
    }
}

