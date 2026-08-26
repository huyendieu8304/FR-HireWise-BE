package com.hirewise.be.controller;

import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.dto.response.JobSummaryResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.JobService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Vị trí tuyển dụng" - internal Job Position list + detail, the entry
 * point on the sidebar from which a Recruiter/Hiring Manager opens a Job's
 * JD (tab "Mô tả chi tiết") and its Kanban board (tab "Kanban Board",
 * see {@link KanbanController}).
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET /api/jobs}          - {@code JOB_VIEW}, scoped to the caller's departments; supports {@code keyword} search on title</li>
 *   <li>{@code GET /api/jobs/{jobId}}  - {@code JOB_VIEW}, scoped to the job's department</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobController {

    JobService jobService;

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
}
