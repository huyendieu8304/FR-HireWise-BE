package com.hirewise.be.controller;

import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.RequiresOwnership;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.JobLifecycleRequestDto;
import com.hirewise.be.dto.request.JobPositionRequestDto;
import com.hirewise.be.dto.request.SubmitJobRequestDto;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.dto.response.JobSummaryResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.JobLifecycleService;
import com.hirewise.be.service.JobService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Vị trí tuyển dụng" - internal Job Position list + detail, the entry
 * point on the sidebar from which a Recruiter/Hiring Manager opens a Job's
 * JD (tab "Mô tả chi tiết") and its Kanban board (tab "Kanban Board",
 * see {@link KanbanController}). UC-12 (draft/edit) and UC-13 (attach
 * Pipeline Template + submit for approval) also live here, on the same
 * {@code /api/jobs} resource.
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET   /api/jobs}            - {@code JOB_VIEW}, scoped to the caller's departments; supports {@code keyword} search on title</li>
 *   <li>{@code GET   /api/jobs/{jobId}}    - {@code JOB_VIEW}, scoped to the job's department</li>
 *   <li>{@code POST  /api/jobs}            - {@code JOB_CREATE}, scoped to the target department</li>
 *   <li>{@code PATCH /api/jobs/{jobId}}    - {@code JOB_EDIT}, scoped to the job's department; only while Draft/Rejected</li>
 *   <li>{@code POST  /api/jobs/{jobId}/submit} - {@code JOB_SUBMIT}, scoped to the job's department; only while Draft/Rejected</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobController {

    JobService jobService;
    JobLifecycleService jobLifecycleService;

    /**
     * Lists every Job Position visible to the caller, with optional
     * department/status filters and a free-text search box (matched
     * against the job title).
     *
     * @param departmentId optional department filter
     * @param status       optional status filter
     * @param keyword      optional search box text — matched against the job title
     * @param page         zero-based page index
     * @param size         page size
     * @param currentUser  authenticated caller, used for authorization
     * @return paginated list of job summaries
     */
    @GetMapping
    public ResponseEntity<PagedResponseDto<JobSummaryResponseDto>> list(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserPrincipal CurrentUser currentUser) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(jobService.listJobs(currentUser, departmentId, status, keyword, pageable));
    }

    /**
     * Full JD detail for one Job Position (tab "Mô tả chi tiết").
     *
     * @param jobId       id of the job position
     * @param currentUser authenticated caller, used for authorization
     * @return the job's full detail
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobDetailResponseDto> getDetail(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobService.getJobDetail(jobId, currentUser));
    }

    /**
     * UC-12 normal flow: creates a new Job Position in {@code DRAFT} status,
     * self-assigned to the calling Recruiter. Requires {@code JOB_CREATE}.
     *
     * @param request     new job's JD fields
     * @param currentUser authenticated caller, used for authorization and as the job's owner
     * @return 201 Created with the created job
     */
    @PostMapping
    public ResponseEntity<JobDetailResponseDto> create(
            @Valid @RequestBody JobPositionRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        JobDetailResponseDto response = jobService.createJob(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UC-12 AF-01: re-saves a Draft (or Rejected) Job Position's fields.
     * Requires {@code JOB_EDIT}.
     *
     * @param jobId       id of the job position to update
     * @param request     the full JD form, resent as-is (not a partial patch)
     * @param currentUser authenticated caller, used for authorization
     * @return the updated job
     */
    @PatchMapping("/{jobId}")
    public ResponseEntity<JobDetailResponseDto> update(
            @PathVariable UUID jobId,
            @Valid @RequestBody JobPositionRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobService.updateDraftJob(jobId, request, currentUser));
    }

    /**
     * UC-13 normal flow: attaches a Pipeline Template to a Draft/Rejected
     * Job Position and submits it for approval. Requires {@code JOB_SUBMIT}.
     *
     * @param jobId       id of the job position to submit
     * @param request     the chosen Pipeline Template
     * @param currentUser authenticated caller, used for authorization
     * @return the updated job, now {@code PENDING_APPROVAL}
     */
    @PostMapping("/{jobId}/submit")
    public ResponseEntity<JobDetailResponseDto> submit(
            @PathVariable UUID jobId,
            @Valid @RequestBody SubmitJobRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobService.submitForApproval(jobId, request, currentUser));
    }

    /**
     * UC-45: publishes an Approved Job Position to the public Job Board.
     * Requires {@code JOB_PUBLISH} and ownership of the job.
     *
     * @param jobId       id of the Approved job to publish
     * @param currentUser authenticated caller, used for authorization
     * @return the updated job, now {@code PUBLISHED}
     */
    @PostMapping("/{jobId}/publish")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_PUBLISH)
    public ResponseEntity<JobDetailResponseDto> publish(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobLifecycleService.publish(jobId, currentUser));
    }

    /**
     * UC-44 normal flow: pauses a Published Job Position - it disappears from
     * the public Job Board and stops accepting applications, but can be
     * resumed at any time. Requires {@code JOB_CLOSE_PAUSE}.
     *
     * @param jobId       id of the Published job to pause
     * @param request     optional reason for the audit trail; body may be omitted
     * @param currentUser authenticated caller, used for authorization
     * @return the updated job, now {@code PAUSED}
     */
    @PostMapping("/{jobId}/pause")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_CLOSE_PAUSE)
    public ResponseEntity<JobDetailResponseDto> pause(
            @PathVariable UUID jobId,
            @Valid @RequestBody(required = false) JobLifecycleRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobLifecycleService.pause(jobId, reasonOf(request), currentUser));
    }

    /**
     * UC-44 normal flow: closes a Job Position for good. {@code CLOSED} is
     * terminal (BR-JOB-05) - there is deliberately no endpoint to undo it.
     * Requires {@code JOB_CLOSE_PAUSE}.
     *
     * @param jobId       id of the Published or Paused job to close
     * @param request     optional reason for the audit trail; body may be omitted
     * @param currentUser authenticated caller, used for authorization
     * @return the updated job, now {@code CLOSED}
     */
    @PostMapping("/{jobId}/close")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_CLOSE_PAUSE)
    public ResponseEntity<JobDetailResponseDto> close(
            @PathVariable UUID jobId,
            @Valid @RequestBody(required = false) JobLifecycleRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobLifecycleService.close(jobId, reasonOf(request), currentUser));
    }

    /**
     * UC-44 AF-01: puts a Paused Job Position back on the Job Board, without
     * a second Hiring Manager approval (BR-JOB-05). Requires
     * {@code JOB_CLOSE_PAUSE}.
     *
     * @param jobId       id of the Paused job to resume
     * @param currentUser authenticated caller, used for authorization
     * @return the updated job, now {@code PUBLISHED} again
     */
    @PostMapping("/{jobId}/resume")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_CLOSE_PAUSE)
    public ResponseEntity<JobDetailResponseDto> resume(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobLifecycleService.resume(jobId, currentUser));
    }

    /**
     * The Confirm Modal reason is optional, so the frontend is allowed to send
     * no body at all - {@code @RequestBody(required = false)} then hands us a
     * {@code null} DTO rather than an empty one.
     */
    private static String reasonOf(JobLifecycleRequestDto request) {
        return request != null ? request.getReason() : null;
    }
}
