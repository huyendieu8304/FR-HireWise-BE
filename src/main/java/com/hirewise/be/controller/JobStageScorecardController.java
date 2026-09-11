package com.hirewise.be.controller;

import com.hirewise.be.dto.request.SaveJobStageScorecardRequestDto;
import com.hirewise.be.dto.response.JobStageScorecardResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.JobStageScorecardService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UC-27 step 3: Hiring Manager (or HR Admin) configures the real Scorecard
 * scoring definition for 1 (Job, Interview-type Stage) pair - either by
 * cloning a Master Template as a starting point or from scratch. Also the
 * entry point for editing it again at any time after the Job is Approved
 * (team decision - the hard gate only blocks Approve itself, not later
 * edits).
 * <p>
 * RBAC: every endpoint requires {@code SCORECARD_TEMPLATE_MANAGE} scoped to
 * the Job's department - see {@code JobStageScorecardService}.
 */
@RestController
@RequestMapping("/api/jobs/{jobId}/pipeline-stages/{pipelineStageId}/scorecard")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobStageScorecardController {

    JobStageScorecardService jobStageScorecardService;

    /**
     * @param jobId           id of the Job
     * @param pipelineStageId id of the Interview-type Stage
     * @return the current version of the Scorecard configured for this pair (404 if none yet)
     */
    @GetMapping
    public ResponseEntity<JobStageScorecardResponseDto> get(
            @PathVariable UUID jobId, @PathVariable Long pipelineStageId, @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobStageScorecardService.getForJobStage(jobId, pipelineStageId, currentUser));
    }

    /**
     * Creates the Scorecard for this pair if none exists yet, edits the
     * current version in place if never graded, or versions it (AF-01) if
     * it already has grading history - see {@code JobStageScorecardService#saveForJobStage}.
     *
     * @param jobId           id of the Job
     * @param pipelineStageId id of the Interview-type Stage
     */
    @PutMapping
    public ResponseEntity<JobStageScorecardResponseDto> save(
            @PathVariable UUID jobId,
            @PathVariable Long pipelineStageId,
            @Valid @RequestBody SaveJobStageScorecardRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobStageScorecardService.saveForJobStage(jobId, pipelineStageId, request, currentUser));
    }
}
