package com.hirewise.be.controller;

import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.RequiresOwnership;
import com.hirewise.be.dto.request.SaveScorecardScoresRequestDto;
import com.hirewise.be.dto.response.ScorecardSubmissionResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.ScorecardSubmissionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UC-28: an evaluator (Interviewer/Hiring Manager) rates a Candidate through
 * a Scorecard Submission for 1 Interview.
 * <p>
 * RBAC:
 * <ul>
 *   <li>{@code GET /api/interviews/{interviewId}/scorecard} - {@code SCORECARD_SUBMIT}
 *       scoped to the Interview's Job's department, PLUS a manual eligibility check
 *       (assigned Interviewer or the Job's Hiring Manager) - see
 *       {@code ScorecardSubmissionService#getOrCreateForm}. No {@code submission_id}
 *       reliably exists yet on first call, so {@link RequiresOwnership} does not apply here.</li>
 *   <li>{@code GET /api/scorecard-submissions/{submissionId}} - {@code APPLICATION_VIEW}
 *       only, deliberately NOT gated by evaluator eligibility/ownership - read-only viewing
 *       of ANY submission (e.g. a Recruiter or a different Interviewer inspecting a
 *       colleague's already-recorded scores) is a different concern from who may score.</li>
 *   <li>{@code PUT/POST /api/scorecard-submissions/{submissionId}/...} - {@code SCORECARD_SUBMIT}
 *       + ownership (must be the submission's own {@code evaluator_id}), enforced by
 *       {@link RequiresOwnership}/{@code OwnershipAspect} via {@code ScorecardSubmissionOwnershipResolver}.</li>
 *   <li>{@code POST .../unlock} - {@code SCORECARD_UNLOCK} (HR Admin only, BR-SCORE-03) -
 *       an admin override, deliberately NOT gated by ownership.</li>
 * </ul>
 */
@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ScorecardController {

    ScorecardSubmissionService scorecardSubmissionService;

    /**
     * UC-28 step 1: the current user's own Scorecard form for this Interview -
     * auto-creates an empty DRAFT submission on first open.
     *
     * @param interviewId id of the Interview being scored
     */
    @GetMapping("/api/interviews/{interviewId}/scorecard")
    public ResponseEntity<ScorecardSubmissionResponseDto> getOrCreateForm(
            @PathVariable UUID interviewId, @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardSubmissionService.getOrCreateForm(interviewId, currentUser));
    }

    /**
     * Read-only detail of any submission (not just the caller's own) for
     * whoever has {@code APPLICATION_VIEW} - lets a Recruiter/HR Admin, or
     * another Interviewer, inspect a colleague's already-recorded
     * criteria/comments without being able to score themselves.
     *
     * @param submissionId id of the submission to view
     */
    @GetMapping("/api/scorecard-submissions/{submissionId}")
    public ResponseEntity<ScorecardSubmissionResponseDto> getSubmissionDetail(
            @PathVariable UUID submissionId, @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardSubmissionService.getSubmissionDetail(submissionId, currentUser));
    }

    /**
     * UC-28 step 2-3: saves progress (does not submit/validate BR-SCORE-01).
     *
     * @param submissionId id of the submission being edited
     */
    @PutMapping("/api/scorecard-submissions/{submissionId}")
    @RequiresOwnership(resourceType = "SCORECARD_SUBMISSION", idParam = "submissionId",
            permission = PermissionCodes.SCORECARD_SUBMIT)
    public ResponseEntity<ScorecardSubmissionResponseDto> saveProgress(
            @PathVariable UUID submissionId,
            @Valid @RequestBody SaveScorecardScoresRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardSubmissionService.saveProgress(submissionId, request, currentUser));
    }

    /**
     * UC-28 step 4-5: validates BR-SCORE-01, computes the Weighted Score
     * (BR-SCORE-02), and finalizes the submission.
     *
     * @param submissionId id of the submission to submit
     */
    @PostMapping("/api/scorecard-submissions/{submissionId}/submit")
    @RequiresOwnership(resourceType = "SCORECARD_SUBMISSION", idParam = "submissionId",
            permission = PermissionCodes.SCORECARD_SUBMIT)
    public ResponseEntity<ScorecardSubmissionResponseDto> submit(
            @PathVariable UUID submissionId, @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardSubmissionService.submit(submissionId, currentUser));
    }

    /**
     * BR-SCORE-03: HR Admin-only override to unlock a submission for editing
     * past the normal 24h window (audited).
     *
     * @param submissionId id of the locked submission to unlock
     */
    @PostMapping("/api/scorecard-submissions/{submissionId}/unlock")
    public ResponseEntity<Void> unlock(
            @PathVariable UUID submissionId, @CurrentUserPrincipal CurrentUser currentUser) {
        scorecardSubmissionService.unlock(submissionId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
