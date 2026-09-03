package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.AiScreeningRun;
import com.hirewise.be.domain.AiScreeningStatus;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationFileRole;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.dto.response.AiScreeningResultResponseDto;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.AiScreeningMapper;
import com.hirewise.be.repository.AiScreeningRunRepository;
import com.hirewise.be.repository.AiSkillMatchRepository;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * UC-21: queues and reads AI Screening Runs for an Application. Actually
 * CALLING the AI Engine happens asynchronously in
 * {@code event.AiScreeningDispatcher} - {@link #enqueueRun} only ever
 * inserts a {@code PENDING} row (or an immediate {@code FAILED} row for an
 * unsupported CV format), never blocking the caller on the Claude API
 * itself (BR-AI-01: AI is support, not a gate on the recruitment flow).
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class AiScreeningService {

    /** UC-21 scope note: only PDF CVs are sent to the AI Engine - see class Javadoc. */
    private static final String PDF_MARKER = "pdf";

    AiScreeningRunRepository aiScreeningRunRepository;
    AiSkillMatchRepository aiSkillMatchRepository;
    ApplicationRepository applicationRepository;
    ApplicationFileRepository applicationFileRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-21 precondition / AF-01: queues a new AI Screening Run for the
     * Application's current primary CV. Called automatically right after a
     * CV is attached ({@code JobApplicationService#apply}, UC-17) and again
     * whenever a Recruiter bấm "Phân tích lại".
     * <p>
     * A CV that isn't a PDF (UC-21 scope note: {@code .doc}/{@code .docx}
     * not read by the AI Engine yet) or an Application with no CV attached
     * at all gets an immediately-{@code FAILED} row instead of
     * {@code PENDING} - EX-01 applies the same way, without the dispatcher
     * having to make a doomed API call first.
     *
     * @param application the Application whose primary CV to analyze
     */
    @Transactional
    public void enqueueRun(Application application) {
        ApplicationFile cvFile = applicationFileRepository
                .findByApplication_IdAndFileRoleAndPrimaryTrue(application.getId(), ApplicationFileRole.CV)
                .orElse(null);

        Instant now = Instant.now(clock);
        if (cvFile == null) {
            saveFailedRun(application, now, "Chưa có CV nào được đính kèm để phân tích.");
            return;
        }

        StoredFile storedFile = cvFile.getFile();
        String mimeType = storedFile.getMimeType() == null ? "" : storedFile.getMimeType().toLowerCase(Locale.ROOT);
        if (!mimeType.contains(PDF_MARKER)) {
            saveFailedRun(application, now, "Định dạng CV chưa được hỗ trợ phân tích AI (chỉ hỗ trợ .pdf).");
            log.info("AI Screening skipped for application {} - CV mimeType={} not PDF",
                    application.getId(), storedFile.getMimeType());
            return;
        }

        AiScreeningRun saved = aiScreeningRunRepository.save(AiScreeningRun.builder()
                .application(application)
                .status(AiScreeningStatus.PENDING)
                .createdAt(now)
                .build());
        log.info("Queued AI Screening run {} for application {}", saved.getId(), application.getId());
    }

    private void saveFailedRun(Application application, Instant now, String errorMessage) {
        aiScreeningRunRepository.save(AiScreeningRun.builder()
                .application(application)
                .status(AiScreeningStatus.FAILED)
                .errorMessage(errorMessage)
                .createdAt(now)
                .completedAt(now)
                .build());
    }

    /**
     * UC-21 AF-01: Recruiter bấm "Phân tích lại" - queues a brand-new run,
     * keeping every previous run's history intact (BR-AI-02).
     *
     * @param applicationId id of the application to re-analyze
     * @param currentUser   authenticated caller, must have {@code AI_VIEW} scoped to the job's department
     * @throws ResourceNotFoundException if no application exists with this id
     */
    @Transactional
    public void runManual(UUID applicationId, CurrentUser currentUser) {
        Application application = loadWithAccessCheck(applicationId, currentUser);
        enqueueRun(application);
    }

    /**
     * UC-21 main flow: the latest AI Screening Run for the Applicant Card's
     * [AI Match Analysis] tab. Returns {@code null} (not a 404) when no run
     * has ever been queued for this Application yet - "chưa có phân tích
     * AI" is a normal state, not an error.
     *
     * @param applicationId id of the application
     * @param currentUser   authenticated caller, must have {@code AI_VIEW} scoped to the job's department
     * @return the latest run's result, or {@code null} if none exists yet
     * @throws ResourceNotFoundException if no application exists with this id
     */
    @Transactional(readOnly = true)
    public AiScreeningResultResponseDto getLatestResult(UUID applicationId, CurrentUser currentUser) {
        loadWithAccessCheck(applicationId, currentUser);

        AiScreeningRun run = aiScreeningRunRepository
                .findFirstByApplication_IdOrderByCreatedAtDesc(applicationId)
                .orElse(null);
        if (run == null) {
            return null;
        }

        List<com.hirewise.be.domain.AiSkillMatch> skillMatches = aiSkillMatchRepository.findByRun_Id(run.getId());
        return AiScreeningMapper.toDto(run, skillMatches);
    }

    private Application loadWithAccessCheck(UUID applicationId, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        JobPosition job = application.getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.AI_VIEW,
                ResourceContext.job(job.getId(), departmentId));

        return application;
    }
}
