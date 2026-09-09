package com.hirewise.be.service;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.JobMapper;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The second half of a Job Position lifecycle, after the Hiring Manager has
 * had their say: UC-45 (publish an Approved job to the Job Board) and UC-44
 * (pause, close, or resume a job that is already live).
 *
 * <p>Kept out of {@link JobService} deliberately. That class already owns the
 * pre-approval half - list, detail, UC-12 draft/edit, UC-13 submit - and is
 * over 400 lines with seven collaborators; the four transitions here share
 * none of its Pipeline Template or Hiring Manager notification machinery, so
 * folding them in would have grown a class that is already at the limit of
 * what {@code guides/01-CODING_CONVENTION.md} section 5 allows.</p>
 *
 * <p><b>Authorization is NOT repeated here.</b> All four entry points sit
 * behind {@code @RequiresOwnership} on {@code JobController}, which runs RBAC
 * layers 2, 3 and 4 through {@code OwnershipAspect} before the method is ever
 * entered - the same contract {@code KanbanService} and
 * {@code ApplicationRejectionService} already rely on. Re-checking would
 * double every permission query for no added safety.</p>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobLifecycleService {

    /**
     * BR-JOB-05: Closed is terminal, so it is absent from every target set
     * except Close's own. Publish deliberately accepts only APPROVED - a job
     * that is already PUBLISHED must go through Pause/Resume, not Publish
     * again, so that {@code audit_logs} tells the truth about what happened.
     */
    private static final Set<JobStatus> PUBLISHABLE_FROM = EnumSet.of(JobStatus.APPROVED);
    private static final Set<JobStatus> PAUSABLE_FROM = EnumSet.of(JobStatus.PUBLISHED);
    private static final Set<JobStatus> CLOSABLE_FROM = EnumSet.of(JobStatus.PUBLISHED, JobStatus.PAUSED);
    private static final Set<JobStatus> RESUMABLE_FROM = EnumSet.of(JobStatus.PAUSED);

    JobPositionRepository jobPositionRepository;
    AuditLogService auditLogService;
    Clock clock;

    /**
     * UC-45: puts an Approved Job Position live on the public Job Board.
     *
     * <p>This is the only code path in the system that produces
     * {@code PUBLISHED}. Everything downstream keys off it without any further
     * change: {@code JobPositionRepository.searchPublished} and
     * {@code findByIdAndStatus(id, PUBLISHED)} already gate both the public
     * board (UC-16) and application intake (UC-17).</p>
     *
     * @param jobId       id of the Approved job to publish
     * @param currentUser the Recruiter who owns this job (enforced by the aspect)
     * @return the updated job
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     * @throws BusinessConflictException if the job is not currently {@code APPROVED}
     */
    @Transactional
    public JobDetailResponseDto publish(UUID jobId, CurrentUser currentUser) {
        JobPosition job = loadOrThrow(jobId);
        requireStatusIn(job, PUBLISHABLE_FROM, JobStatus.PUBLISHED, ErrorCode.JOB_POSITION_NOT_PUBLISHABLE);

        JobStatus previous = job.getStatus();
        applyStatus(job, JobStatus.PUBLISHED);
        auditLogService.record(currentUser.userId(), "JOB_PUBLISHED", "job_positions", jobId.toString(),
                statusJson(previous, null), statusJson(JobStatus.PUBLISHED, null));

        log.info("UC-45: job {} published to the Job Board by user {}", jobId, currentUser.userId());
        return JobMapper.toDetailDto(job);
    }

    /**
     * UC-44 normal flow, [Tam dung]: stops new applications and hides the job
     * from the public Job Board, keeping every existing candidate and
     * Application untouched. Reversible via {@link #resume}.
     *
     * @param jobId       id of the Published job to pause
     * @param reason      optional free-text reason, stored in the audit trail only
     * @param currentUser the Recruiter who owns this job, or an HR Admin
     * @return the updated job
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     * @throws BusinessConflictException if the job is not currently {@code PUBLISHED}
     */
    @Transactional
    public JobDetailResponseDto pause(UUID jobId, String reason, CurrentUser currentUser) {
        return transition(jobId, reason, currentUser, PAUSABLE_FROM, JobStatus.PAUSED, "JOB_PAUSED");
    }

    /**
     * UC-44 normal flow, [Dong vi tri]: permanently stops recruitment for this
     * position. {@code CLOSED} is terminal (BR-JOB-05) - reopening is not a
     * feature, the Recruiter has to create a new Job Position (UC-12).
     *
     * @param jobId       id of the Published or Paused job to close
     * @param reason      optional free-text reason, stored in the audit trail only
     * @param currentUser the Recruiter who owns this job, or an HR Admin
     * @return the updated job
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     * @throws BusinessConflictException if the job is neither {@code PUBLISHED} nor {@code PAUSED}
     */
    @Transactional
    public JobDetailResponseDto close(UUID jobId, String reason, CurrentUser currentUser) {
        return transition(jobId, reason, currentUser, CLOSABLE_FROM, JobStatus.CLOSED, "JOB_CLOSED");
    }

    /**
     * UC-44 AF-01, [Mo lai]: puts a Paused job straight back on the Job Board.
     *
     * <p>BR-JOB-05 is explicit that this does <b>not</b> go back through the
     * Hiring Manager - the approval already on file still stands, so no
     * {@code JobApproval} row is created and no EM-02 notification is sent.</p>
     *
     * @param jobId       id of the Paused job to resume
     * @param currentUser the Recruiter who owns this job, or an HR Admin
     * @return the updated job
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     * @throws BusinessConflictException if the job is not currently {@code PAUSED}
     */
    @Transactional
    public JobDetailResponseDto resume(UUID jobId, CurrentUser currentUser) {
        return transition(jobId, null, currentUser, RESUMABLE_FROM, JobStatus.PUBLISHED, "JOB_RESUMED");
    }

    private JobDetailResponseDto transition(UUID jobId, String reason, CurrentUser currentUser,
                                            Set<JobStatus> allowedFrom, JobStatus target, String auditAction) {
        JobPosition job = loadOrThrow(jobId);
        requireStatusIn(job, allowedFrom, target, ErrorCode.JOB_STATUS_TRANSITION_NOT_ALLOWED);

        JobStatus previous = job.getStatus();
        applyStatus(job, target);
        auditLogService.record(currentUser.userId(), auditAction, "job_positions", jobId.toString(),
                statusJson(previous, null), statusJson(target, reason));

        log.info("UC-44: job {} moved {} -> {} by user {}", jobId, previous, target, currentUser.userId());
        return JobMapper.toDetailDto(job);
    }

    private JobPosition loadOrThrow(UUID jobId) {
        return jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));
    }

    /**
     * @param errorCode which message the caller sees - publish has its own
     *                  ({@code JOB_POSITION_NOT_PUBLISHABLE}) because "only an
     *                  Approved job can be published" is far more useful to a
     *                  Recruiter than the generic from/to wording
     */
    private static void requireStatusIn(JobPosition job, Set<JobStatus> allowedFrom,
                                        JobStatus target, ErrorCode errorCode) {
        if (!allowedFrom.contains(job.getStatus())) {
            throw new BusinessConflictException(errorCode, job.getStatus(), target);
        }
    }

    private void applyStatus(JobPosition job, JobStatus target) {
        job.setStatus(target);
        job.setUpdatedAt(Instant.now(clock));
        jobPositionRepository.save(job);
    }

    /**
     * Builds the audit snapshot by hand instead of pulling in Jackson: only two
     * short, fully-controlled values ever go in, and the reason is escaped for
     * the handful of characters that can break JSON.
     */
    private static String statusJson(JobStatus status, String reason) {
        if (reason == null || reason.isBlank()) {
            return "{\"status\":\"" + status + "\"}";
        }
        return "{\"status\":\"" + status + "\",\"reason\":\"" + escapeJson(reason) + "\"}";
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
