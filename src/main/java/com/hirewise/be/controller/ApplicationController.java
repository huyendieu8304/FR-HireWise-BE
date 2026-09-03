package com.hirewise.be.controller;

import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.RequiresOwnership;
import com.hirewise.be.dto.request.MoveApplicationStageRequestDto;
import com.hirewise.be.dto.request.RejectApplicationRequestDto;
import com.hirewise.be.dto.response.AiScreeningResultResponseDto;
import com.hirewise.be.dto.response.ApplicationDetailResponseDto;
import com.hirewise.be.dto.response.ApplicationRejectionResponseDto;
import com.hirewise.be.dto.response.FileDownloadResponseDto;
import com.hirewise.be.dto.response.MoveApplicationStageResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.*;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * Application-scoped endpoints: UC-20 (Applicant Card detail) and UC-23
 * (Kanban drag-and-drop stage transition). UC-29 (Reject Application) lives
 * on its own {@code POST /{applicationId}/reject} below.
 * <p>
 * RBAC:
 * <ul>
 *   <li>{@code GET /api/applications/{applicationId}}         - {@code APPLICATION_VIEW}, scoped to the job's department</li>
 *   <li>{@code PATCH /api/applications/{applicationId}/stage} - {@code APPLICATION_MOVE_STAGE} + ownership of the parent Job as its Recruiter
 *       (RBAC.md section 4: {@code application.job.recruiter_id}), both enforced by {@link RequiresOwnership}/{@code OwnershipAspect}</li>
 *   <li>{@code POST /api/applications/{applicationId}/reject}  - {@code APPLICATION_REJECT}, same ownership rule as above</li>
 *   <li>{@code GET  /api/applications/{applicationId}/ai-screening}     - {@code AI_VIEW}, scoped to the job's department (UC-21)</li>
 *   <li>{@code POST /api/applications/{applicationId}/ai-screening/run} - {@code AI_VIEW}, scoped to the job's department (UC-21 AF-01)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/applications")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ApplicationController {

    ApplicationService applicationService;
    ApplicationRejectionService applicationRejectionService;
    KanbanService kanbanService;
    AiScreeningService aiScreeningService;
    InterviewService interviewService;

    /**
     * UC-20 main flow: the Applicant Card - full detail of one Candidate's
     * Application.
     *
     * @param applicationId id of the application
     * @param currentUser   authenticated caller, used for authorization
     * @return the Applicant Card detail
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<ApplicationDetailResponseDto> getDetail(
            @PathVariable UUID applicationId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(applicationService.getDetail(applicationId, currentUser));
    }

    /**
     * UC-23 main flow: moves an Application to a different Kanban column
     * (Pipeline Stage) and records the change (BR-KANBAN-01).
     *
     * @param applicationId id of the application being moved
     * @param request       target stage id
     * @param currentUser   authenticated caller - must own the parent Job (as its Recruiter)
     * @return the application's new stage/status/timestamp
     */
    @PatchMapping("/{applicationId}/stage")
    @RequiresOwnership(resourceType = "APPLICATION", idParam = "applicationId",
            permission = PermissionCodes.APPLICATION_MOVE_STAGE)
    public ResponseEntity<MoveApplicationStageResponseDto> moveStage(
            @PathVariable UUID applicationId,
            @Valid @RequestBody MoveApplicationStageRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(kanbanService.moveStage(applicationId, request, currentUser));
    }

    /**
     * UC-29 main flow: rejects an Application - picks a standardized reason
     * (BR-REJ-01), moves it to the pipeline's Terminal-Rejected stage
     * (BR-KANBAN-01 history included), and enqueues the automatic rejection
     * email (UC-30).
     *
     * @param applicationId id of the application being rejected
     * @param request       standardized reason id + optional custom message
     * @param currentUser   authenticated caller - must own the parent Job (as its Recruiter)
     * @return the rejection record just created
     */
    @PostMapping("/{applicationId}/reject")
    @RequiresOwnership(resourceType = "APPLICATION", idParam = "applicationId",
            permission = PermissionCodes.APPLICATION_REJECT)
    public ResponseEntity<ApplicationRejectionResponseDto> reject(
            @PathVariable UUID applicationId,
            @Valid @RequestBody RejectApplicationRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(applicationRejectionService.reject(applicationId, request, currentUser));
    }

    /**
     * UC-24: schedules an interview session, assigns interviewers, transitions
     * the application to the INTERVIEW stage, and enqueues EM-05 and EM-08 emails.
     *
     * @param applicationId id of the application being scheduled
     * @param request       interview details
     * @param currentUser   authenticated caller - must own the parent Job (as its Recruiter)
     * @return the created interview details and stage move outcome
     */
    @PostMapping("/{applicationId}/interviews")
    @RequiresOwnership(resourceType = "APPLICATION", idParam = "applicationId",
            permission = PermissionCodes.INTERVIEW_SCHEDULE)
    public ResponseEntity<com.hirewise.be.dto.response.ScheduleInterviewResponseDto> scheduleInterview(
            @PathVariable UUID applicationId,
            @Valid @RequestBody com.hirewise.be.dto.request.ScheduleInterviewRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(interviewService.scheduleInterview(applicationId, request, currentUser));
    }

    /**
     * UC-24: returns active users who can be assigned as interviewers.
     */
    @GetMapping("/interviewers")
    public ResponseEntity<java.util.List<com.hirewise.be.dto.response.InterviewerOptionDto>> getInterviewers(
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(interviewService.getAvailableInterviewers(currentUser));
    }

    /**
     * UC-24: returns scheduled interviews within a date range for the visual calendar grid.
     */
    @GetMapping("/interviews/calendar")
    public ResponseEntity<java.util.List<com.hirewise.be.dto.response.InterviewCalendarDto>> getInterviewCalendar(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(interviewService.getScheduleCalendar(startDate, endDate, currentUser));
    }



    /**
     * UC-20 file view: returns a short-lived URL to view or download one of an
     * Application's attached files from Cloud Storage. The caller opens this
     * URL in a new tab to read the CV/cover-letter/portfolio without the backend
     * having to proxy the file bytes.
     * <p>
     * RBAC: {@code APPLICATION_VIEW} scoped to the application's job's department
     * (same gate as {@link #getDetail}).
     *
     * @param applicationId id of the parent Application
     * @param fileId        id of the ApplicationFile record (from the detail response)
     * @param currentUser   authenticated caller
     * @return {@code { "viewUrl": "https://..." }}
     */
    @GetMapping("/{applicationId}/files/{fileId}/view-url")
    public ResponseEntity<java.util.Map<String, String>> getFileViewUrl(
            @PathVariable UUID applicationId,
            @PathVariable Long fileId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        String viewUrl = applicationService.getFileViewUrl(applicationId, fileId, currentUser);
        return ResponseEntity.ok(java.util.Map.of("viewUrl", viewUrl));
    }

    /**
     * Proxies the download of a file through the backend. This is used
     * so internal users don't need direct Google Drive/Dropbox permissions
     * on the actual file.
     */
    @GetMapping("/{applicationId}/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable UUID applicationId,
            @PathVariable Long fileId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        FileDownloadResponseDto result = applicationService.downloadApplicationFile(applicationId, fileId, currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.mimeType()))
                .body(result.content());
    }

    /**
     * UC-21 main flow: the latest AI Screening Run for the Applicant Card's
     * [AI Match Analysis] tab. Returns an empty 200 body (never 404) when no
     * run has been queued yet - "chưa có phân tích AI" is a normal state.
     *
     * @param applicationId id of the application
     * @param currentUser   authenticated caller, used for authorization
     * @return the latest run's result, or {@code null} if none exists yet
     */
    @GetMapping("/{applicationId}/ai-screening")
    public ResponseEntity<AiScreeningResultResponseDto> getAiScreeningResult(
            @PathVariable UUID applicationId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(aiScreeningService.getLatestResult(applicationId, currentUser));
    }

    /**
     * UC-21 AF-01: Recruiter bấm "Phân tích lại" - queues a brand-new AI
     * Screening Run (picked up asynchronously by
     * {@code event.AiScreeningDispatcher}), keeping every previous run's
     * history intact (BR-AI-02).
     *
     * @param applicationId id of the application to re-analyze
     * @param currentUser   authenticated caller, used for authorization
     * @return 202 Accepted - the run is queued, not completed yet
     */
    @PostMapping("/{applicationId}/ai-screening/run")
    public ResponseEntity<Void> runAiScreening(
            @PathVariable UUID applicationId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        aiScreeningService.runManual(applicationId, currentUser);
        return ResponseEntity.accepted().build();
    }
}
