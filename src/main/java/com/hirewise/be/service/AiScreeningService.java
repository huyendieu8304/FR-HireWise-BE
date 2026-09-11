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
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.dto.response.AiScreeningBatchResponseDto;
import com.hirewise.be.dto.response.AiScreeningResultResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.AiScreeningMapper;
import com.hirewise.be.repository.AiScreeningRunRepository;
import com.hirewise.be.repository.AiSkillMatchRepository;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PipelineStageRepository;
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
    JobPositionRepository jobPositionRepository;
    PipelineStageRepository pipelineStageRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-21 precondition / AF-01: queues a new AI Screening Run for the
     * Application's current primary CV. AI Screening KHÔNG còn tự động chạy
     * khi Candidate nộp hồ sơ (UC-17) nữa - Recruiter chủ động bấm "Phân
     * tích lại" trên 1 Application ({@link #runManual}) hoặc "Quét cả cột"
     * trên 1 Stage ({@link #runBatchForStage}) mới gọi tới đây.
     * <p>
     * A CV that isn't a PDF (UC-21 scope note: {@code .doc}/{@code .docx}
     * not read by the AI Engine yet) or an Application with no CV attached
     * at all gets an immediately-{@code FAILED} row instead of
     * {@code PENDING} - EX-01 applies the same way, without the dispatcher
     * having to make a doomed API call first.
     *
     * @param application the Application whose primary CV to analyze
     * @return the just-saved run (PENDING nếu đủ điều kiện, FAILED ngay nếu
     *         thiếu CV/CV không phải PDF) - {@link #runBatchForStage} dùng
     *         status này để đếm queued/skipped mà không cần query lại.
     */
    @Transactional
    public AiScreeningRun enqueueRun(Application application) {
        ApplicationFile cvFile = applicationFileRepository
                .findByApplication_IdAndFileRoleAndPrimaryTrue(application.getId(), ApplicationFileRole.CV)
                .orElse(null);

        Instant now = Instant.now(clock);
        if (cvFile == null) {
            return saveFailedRun(application, now, "Chưa có CV nào được đính kèm để phân tích.");
        }

        StoredFile storedFile = cvFile.getFile();
        String mimeType = storedFile.getMimeType() == null ? "" : storedFile.getMimeType().toLowerCase(Locale.ROOT);
        if (!mimeType.contains(PDF_MARKER)) {
            log.info("AI Screening skipped for application {} - CV mimeType={} not PDF",
                    application.getId(), storedFile.getMimeType());
            return saveFailedRun(application, now, "Định dạng CV chưa được hỗ trợ phân tích AI (chỉ hỗ trợ .pdf).");
        }

        AiScreeningRun saved = aiScreeningRunRepository.save(AiScreeningRun.builder()
                .application(application)
                .status(AiScreeningStatus.PENDING)
                .createdAt(now)
                .build());
        log.info("Queued AI Screening run {} for application {}", saved.getId(), application.getId());
        return saved;
    }

    private AiScreeningRun saveFailedRun(Application application, Instant now, String errorMessage) {
        return aiScreeningRunRepository.save(AiScreeningRun.builder()
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
     * "Quét cả cột": Recruiter bấm 1 nút trên 1 cột Kanban (thường là cột
     * "Mới"/{@code INTAKE}) để enqueue AI Screening Run cho MỌI Application
     * CHƯA từng phân tích thành công của Job này đang nằm ở đúng Stage đó,
     * thay vì bấm "Phân tích lại" từng ứng viên một. Application nào đã có
     * {@link Application#getAiMatchScore()} (khác {@code null} - tức đã có
     * ít nhất 1 run {@code SUCCEEDED}) bị BỎ QUA hoàn toàn, không tốn thêm
     * 1 lời gọi Claude API nào cho hồ sơ đã có kết quả - muốn phân tích lại
     * 1 hồ sơ cụ thể dù đã có điểm thì dùng nút "Phân tích lại" trên chính
     * Applicant Card đó ({@link #runManual}), nút này không phải để làm
     * việc đó. Dùng lại {@link #enqueueRun} cho từng Application còn lại
     * nên giữ nguyên mọi rule cũ (CV không phải PDF/chưa có CV → FAILED
     * ngay; có CV PDF → PENDING, {@code event.AiScreeningDispatcher} xử lý
     * bất đồng bộ).
     *
     * @param jobId       id của Job đang xem Kanban board (đường dẫn chứa `stageId`)
     * @param stageId     id của Stage (cột) muốn quét
     * @param currentUser caller, phải có {@code AI_VIEW} scoped theo department của Job
     * @return tổng số Application ở Stage này, cùng số queued (PENDING) / skipped (FAILED
     *         ngay do thiếu/sai CV) / alreadyAnalyzed (có điểm AI từ trước, không đụng tới)
     * @throws ResourceNotFoundException nếu Job hoặc Stage không tồn tại
     * @throws BadRequestException       nếu Stage không thuộc Pipeline Template của Job này
     *                                    (vd Stage của 1 Pipeline Template khác, hoặc Job
     *                                    chưa gán Pipeline Template)
     */
    @Transactional
    public AiScreeningBatchResponseDto runBatchForStage(UUID jobId, Long stageId, CurrentUser currentUser) {
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));

        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.AI_VIEW,
                ResourceContext.job(jobId, departmentId));

        PipelineStage stage = pipelineStageRepository.findById(stageId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIPELINE_STAGE_NOT_FOUND, stageId));
        // Cùng rule "khác pipeline template thì không hợp lệ" như KanbanService#moveStage -
        // 1 Stage id hợp lệ nhưng thuộc pipeline của Job KHÁC (hoặc Job này chưa gán pipeline
        // nào) không được phép quét, tránh lộ/đụng dữ liệu chéo Job.
        if (job.getPipelineTemplate() == null
                || !stage.getPipelineTemplate().getId().equals(job.getPipelineTemplate().getId())) {
            throw new BadRequestException(ErrorCode.INVALID_STAGE_TRANSITION);
        }

        List<Application> applications = applicationRepository.findByJobPosition_IdAndCurrentStage_Id(jobId, stageId);

        int queuedCount = 0;
        int skippedCount = 0;
        int alreadyAnalyzedCount = 0;
        for (Application application : applications) {
            if (application.getAiMatchScore() != null) {
                alreadyAnalyzedCount++;
                continue;
            }
            AiScreeningRun run = enqueueRun(application);
            if (run.getStatus() == AiScreeningStatus.PENDING) {
                queuedCount++;
            } else {
                skippedCount++;
            }
        }

        log.info("Batch AI Screening: job={} stage={} total={} queued={} skipped={} alreadyAnalyzed={} triggeredBy={}",
                jobId, stageId, applications.size(), queuedCount, skippedCount, alreadyAnalyzedCount, currentUser.userId());

        return AiScreeningBatchResponseDto.builder()
                .totalApplications(applications.size())
                .queuedCount(queuedCount)
                .skippedCount(skippedCount)
                .alreadyAnalyzedCount(alreadyAnalyzedCount)
                .build();
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
