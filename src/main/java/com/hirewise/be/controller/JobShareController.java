package com.hirewise.be.controller;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.RequiresOwnership;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.dto.response.JobShareStatsResponseDto;
import com.hirewise.be.dto.response.JobShareTargetsResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.JobShareService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * UC-31 (chia sẻ ra kênh ngoài) and UC-32 (theo dõi hiệu quả), both hanging
 * off a single Job Position.
 *
 * <p>RBAC deliberately differs between the two:</p>
 * <ul>
 *   <li>The share endpoints are writes that only the Job's own Recruiter may
 *       perform, so they carry {@code @RequiresOwnership} with
 *       {@code JOB_PUBLISH} - the same permission and the same ownership rule
 *       as publishing the Job in the first place.</li>
 *   <li>The stats endpoint is a read that a Hiring Manager or HR Admin also
 *       has a legitimate reason to open, so it uses a plain {@code JOB_VIEW}
 *       scope check instead. Requiring ownership there would lock the numbers
 *       away from exactly the people who ask for them.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs/{jobId}")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobShareController {

    JobShareService jobShareService;
    AccessControlService accessControlService;

    /**
     * UC-31 steps 1-2: the Open Graph preview plus every enabled channel, with
     * share and popup URLs already built.
     *
     * @param jobId       id of the Published job to share
     * @param currentUser authenticated caller, used for authorization
     * @return the preview and the channels on offer
     */
    @GetMapping("/share-channels")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_PUBLISH)
    public ResponseEntity<JobShareTargetsResponseDto> shareTargets(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(jobShareService.listShareTargets(jobId));
    }

    /**
     * UC-31 step 3: records that [Chia sẻ] was pressed for this channel.
     *
     * <p>Returns 204 rather than the updated counters on purpose - the
     * frontend fires this immediately after opening the platform popup and has
     * nothing to render from the response; the share modal refreshes its own
     * query when it closes.</p>
     *
     * @param jobId       id of the Published job being shared
     * @param channelCode which channel, e.g. {@code linkedin}
     * @param currentUser authenticated caller, used for authorization
     */
    @PostMapping("/share-channels/{channelCode}/record")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_PUBLISH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordShare(
            @PathVariable UUID jobId,
            @PathVariable String channelCode,
            @CurrentUserPrincipal CurrentUser currentUser) {
        jobShareService.recordShare(jobId, PublishingChannelCode.from(channelCode), currentUser);
    }

    /**
     * UC-32 step 4: queues the EM-10 summary email to the Recruiter, called
     * when the share modal is closed after at least one share.
     *
     * @param jobId       id of the shared job
     * @param currentUser authenticated caller, used for authorization
     */
    @PostMapping("/share-channels/notify")
    @RequiresOwnership(resourceType = "JOB_POSITION", idParam = "jobId",
            permission = PermissionCodes.JOB_PUBLISH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notifyShareSummary(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        jobShareService.notifyShareSummary(jobId, currentUser);
    }

    /**
     * UC-32 steps 1-2: shares, clicks and attributed applications per channel.
     *
     * @param jobId       id of the job to report on
     * @param currentUser authenticated caller, must have {@code JOB_VIEW} in this job's scope
     * @return per-channel statistics plus the totals
     */
    @GetMapping("/share-stats")
    public ResponseEntity<JobShareStatsResponseDto> shareStats(
            @PathVariable UUID jobId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        // Scoped by job rather than department: the caller is asking about this
        // one Job, and ResourceContext.job carries both ids the scope check needs.
        accessControlService.checkAccess(currentUser, PermissionCodes.JOB_VIEW, ResourceContext.job(jobId, null));
        return ResponseEntity.ok(jobShareService.getShareStats(jobId));
    }
}
