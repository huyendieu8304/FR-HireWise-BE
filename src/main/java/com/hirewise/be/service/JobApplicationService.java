package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationFileRole;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.CandidateStatus;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.dto.request.SubmitApplicationRequestDto;
import com.hirewise.be.dto.response.SubmitApplicationResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.logging.LogMaskUtils;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.CandidateRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UC-17: an anonymous candidate applies to a Published job. The whole
 * normal flow (steps 2-7) plus AF-01 (repeat applicant) and EX-01/EX-02/
 * EX-03 run inside {@link #apply}, in a single transaction - a partial
 * application (e.g. Candidate/Application rows created but the CV upload
 * failed outright) is not an acceptable state to leave behind.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobApplicationService {

    /** BR-APPLY-01: 10MB max. */
    private static final long MAX_CV_SIZE_BYTES = 10L * 1024 * 1024;

    /** BR-APPLY-01: only these three formats are accepted. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    JobPositionRepository jobPositionRepository;
    CandidateRepository candidateRepository;
    ApplicationRepository applicationRepository;
    ApplicationFileRepository applicationFileRepository;
    ApplicationStageHistoryRepository applicationStageHistoryRepository;
    PipelineStageRepository pipelineStageRepository;
    FileStorageService fileStorageService;
    OutboxEventPublisher outboxEventPublisher;
    AuditLogService auditLogService;
    AiScreeningService aiScreeningService;
    Clock clock;

    /**
     * UC-17 normal flow steps 2-7 / AF-01 / EX-01..EX-03.
     *
     * @param jobId   the Published job being applied to (path variable)
     * @param request contact info (full name / email / phone)
     * @param cvFile  the uploaded CV
     * @return the created (or updated, for AF-01) application's id
     * @throws ResourceNotFoundException if the job doesn't exist or isn't Published
     * @throws BadRequestException       if the CV file is missing, the wrong format, or too large (EX-01)
     */
    @Transactional
    public SubmitApplicationResponseDto apply(UUID jobId, SubmitApplicationRequestDto request, MultipartFile cvFile) {
        JobPosition job = jobPositionRepository.findByIdAndStatus(jobId, JobStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));

        validateCv(cvFile);

        Instant now = Instant.now(clock);
        Candidate candidate = upsertCandidate(request, now);

        if (candidate.getStatus() == CandidateStatus.BLACKLISTED) {
            // BR-APPLY-03: flag for the Recruiter's attention, never auto-reject.
            auditLogService.record(null, "CANDIDATE_BLACKLIST_APPLY_FLAGGED", "candidates", candidate.getId().toString());
            log.warn("Blacklisted candidateId={} applied to jobId={}", candidate.getId(), job.getId());
        }

        Optional<Application> existing = applicationRepository.findByCandidate_IdAndJobPosition_Id(candidate.getId(), job.getId());
        boolean duplicate = existing.isPresent();
        Application application = duplicate
                ? updateExistingApplication(existing.get(), now)
                : createNewApplication(candidate, job, now);

        String safeFileName = buildSafeFileName(job, candidate, cvFile.getOriginalFilename());
        String subfolderName = job.getId().toString() + "/" + application.getId().toString();
        
        StoredFile storedFile = fileStorageService.storeCv(cvFile, subfolderName, safeFileName);

        attachCvFile(application, storedFile, duplicate, now);

        // UC-21 precondition: queue an AI Screening Run now that the CV is attached -
        // only ever inserts a row (PENDING or an immediate FAILED for an unsupported
        // format), never calls the AI Engine itself on this request thread.
        aiScreeningService.enqueueRun(application);

        outboxEventPublisher.publish(OutboxEventType.APPLICATION_CONFIRMATION_EMAIL,
                OutboxPayloads.applicationConfirmationEmail(candidate.getPrimaryEmail(), candidate.getFullName(), job.getTitle()));

        log.info("Application {} for jobId={} by candidateId={} (email={}, duplicate={})",
                application.getId(), job.getId(), candidate.getId(), LogMaskUtils.maskEmail(candidate.getPrimaryEmail()), duplicate);

        return SubmitApplicationResponseDto.builder()
                .applicationId(application.getId())
                .duplicate(duplicate)
                .build();
    }

    private Candidate upsertCandidate(SubmitApplicationRequestDto request, Instant now) {
        return candidateRepository.findByPrimaryEmailIgnoreCase(request.getEmail())
                .map(existing -> {
                    existing.setFullName(request.getFullName());
                    existing.setPhone(request.getPhone());
                    existing.setUpdatedAt(now);
                    return candidateRepository.save(existing);
                })
                .orElseGet(() -> candidateRepository.save(Candidate.builder()
                        .id(UUID.randomUUID())
                        .fullName(request.getFullName())
                        .primaryEmail(request.getEmail())
                        .phone(request.getPhone())
                        .status(CandidateStatus.ACTIVE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }

    /** place a brand-new Application into the Job's Pipeline first stage, and log the initial history event. */
    private Application createNewApplication(Candidate candidate, JobPosition job, Instant now) {
        if (job.getPipelineTemplate() == null) {
            // Defensive: UC-13 is supposed to guarantee every job has a pipeline before it can be approved/published.
            throw new BusinessConflictException(ErrorCode.PIPELINE_NOT_CONFIGURED, job.getId());
        }
        PipelineStage firstStage = pipelineStageRepository
                .findFirstByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(job.getPipelineTemplate().getId())
                .orElseThrow(() -> new BusinessConflictException(ErrorCode.PIPELINE_NOT_CONFIGURED, job.getId()));

        Application application = Application.builder()
                .id(UUID.randomUUID())
                .candidate(candidate)
                .jobPosition(job)
                .currentStage(firstStage)
                .status(ApplicationStatus.NEW)
                .appliedAt(now)
                .lastStageChangedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        application = applicationRepository.save(application);

        ApplicationStageHistory history = ApplicationStageHistory.builder()
                .application(application)
                .fromStage(null)
                .toStage(firstStage)
                .changedBy(null)
                .transitionType(StageTransitionType.SYSTEM)
                .changedAt(now)
                .build();
        applicationStageHistoryRepository.save(history);

        return application;
    }

    /** AF-01: a repeat applicant for the same job - touch the existing row rather than inserting a duplicate. */
    private Application updateExistingApplication(Application existing, Instant now) {
        existing.setUpdatedAt(now);
        return applicationRepository.save(existing);
    }

    /** AF-01: demote any previous CV(s) to non-primary before attaching the newly uploaded one. */
    private void attachCvFile(Application application, StoredFile storedFile, boolean duplicate, Instant now) {
        if (duplicate) {
            List<ApplicationFile> previousCvs = applicationFileRepository
                    .findByApplication_IdAndFileRole(application.getId(), ApplicationFileRole.CV);
            previousCvs.forEach(f -> f.setPrimary(false));
            applicationFileRepository.saveAll(previousCvs);
        }
        ApplicationFile applicationFile = ApplicationFile.builder()
                .application(application)
                .file(storedFile)
                .fileRole(ApplicationFileRole.CV)
                .primary(true)
                .createdAt(now)
                .build();
        applicationFileRepository.save(applicationFile);
    }

    /** BR-APPLY-01 / EX-01. */
    private void validateCv(MultipartFile cvFile) {
        if (cvFile == null || cvFile.isEmpty()) {
            throw new BadRequestException(ErrorCode.INVALID_CV_FILE);
        }
        if (cvFile.getSize() > MAX_CV_SIZE_BYTES) {
            throw new BadRequestException(ErrorCode.INVALID_CV_FILE);
        }
        String contentType = cvFile.getContentType() == null ? "" : cvFile.getContentType().toLowerCase(Locale.ROOT);
        String extension = extensionOf(cvFile.getOriginalFilename());
        boolean typeOk = ALLOWED_CONTENT_TYPES.contains(contentType);
        boolean extensionOk = ALLOWED_EXTENSIONS.contains(extension);
        // Some browsers send a generic "application/octet-stream" content type - fall back to
        // trusting the file extension in that case rather than rejecting a legitimate CV.
        if (!typeOk && !extensionOk) {
            throw new BadRequestException(ErrorCode.INVALID_CV_FILE);
        }
    }

    private static String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String buildSafeFileName(JobPosition job, Candidate candidate, String originalFileName) {
        String extension = extensionOf(originalFileName);
        String base = "CV_" + job.getId() + "_" + candidate.getId();
        return StringUtils.hasText(extension) ? base + "." + extension : base;
    }
}
