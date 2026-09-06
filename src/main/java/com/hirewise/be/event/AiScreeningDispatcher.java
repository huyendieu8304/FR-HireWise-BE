package com.hirewise.be.event;

import com.hirewise.be.ai.MatchAnalysisResult;
import com.hirewise.be.ai.MatchingEngine;
import com.hirewise.be.domain.AiMatchType;
import com.hirewise.be.domain.AiScreeningRun;
import com.hirewise.be.domain.AiScreeningStatus;
import com.hirewise.be.domain.AiSkillMatch;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationFileRole;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.repository.AiScreeningRunRepository;
import com.hirewise.be.repository.AiSkillMatchRepository;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.service.FileStorageService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Polls {@code ai_screening_runs} for {@code PENDING} rows and actually
 * calls the {@link MatchingEngine} (Claude API) for each one - same shape
 * as {@link OutboxDispatcher}, applied to UC-21's "queue now, call the AI
 * Engine later, off the request thread" flow instead of email sending.
 * <p>
 * A call that fails or times out (EX-01) marks the run {@code FAILED} with
 * {@code error_message} - it is NOT retried on the next poll (unlike the
 * Outbox's attempts/max-attempts retry), since a fresh AI Screening Run is
 * always just an AF-01 "Phân tích lại" click away and retrying a possibly
 * expensive LLM call automatically is not worth the token spend for a
 * support-only feature (BR-AI-01).
 * <p>
 * {@link #dispatchPendingRuns} calls {@link #dispatchOne} through a
 * self-injected proxy ({@link #self}), not a plain {@code this} call -
 * {@code @Transactional} is applied by a Spring AOP proxy wrapping this
 * bean, which a same-class {@code this.dispatchOne(...)} call bypasses
 * entirely (a well-known Spring self-invocation gotcha). Without going
 * through the proxy, {@code dispatchOne} would run with no transaction/
 * session bound to the thread at all, and the first lazy field it touches
 * (e.g. {@code AiScreeningRun#getApplication()}) throws
 * {@code LazyInitializationException: could not initialize proxy - no session}.
 * <p>
 * Each poll's batch of up to {@code batchSize} {@code PENDING} runs is
 * fanned out across a small fixed thread pool ({@link #dispatchExecutor},
 * sized by {@code app.ai.concurrency}) instead of a plain sequential loop -
 * each {@link #dispatchOne} call blocks on 1-2 slow network round-trips
 * (Cloud Storage download, then the Claude API call itself), so processing
 * runs one at a time meant a queue of N pending runs took roughly
 * N × (single-run latency) wall-clock time even though nothing was actually
 * broken - just serial by construction. This matters a lot more since the
 * Kanban "Quét cả cột" bulk button (UC-21) can enqueue many runs from a
 * single click: a Recruiter's very next "Phân tích lại" on 1 candidate would
 * otherwise sit in FIFO ({@code findBatchByStatus} orders oldest-first)
 * behind that entire batch. {@code batchSize} default (10) intentionally
 * stays higher than {@code app.ai.concurrency} default (3) - Hikari's pool
 * ({@code spring.datasource.hikari.maximum-pool-size}, default 5) still
 * needs headroom left for ordinary web request threads while a poll tick is
 * running its DB-writing transactions concurrently.
 */
@Slf4j
@Component
public class AiScreeningDispatcher {

    /** ISO 32000 PDF header magic number - the first 5 bytes of every valid .pdf file. */
    private static final byte[] PDF_MAGIC_NUMBER = {'%', 'P', 'D', 'F', '-'};

    private final AiScreeningRunRepository aiScreeningRunRepository;
    private final AiSkillMatchRepository aiSkillMatchRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationFileRepository applicationFileRepository;
    private final FileStorageService fileStorageService;
    private final MatchingEngine matchingEngine;
    private final Clock clock;
    private final int batchSize;
    /** Self-injected PROXY (not {@code this}) - see class Javadoc. */
    private final AiScreeningDispatcher self;
    /** Fans out 1 poll tick's batch concurrently instead of 1-at-a-time - see class Javadoc. */
    private final ExecutorService dispatchExecutor;

    public AiScreeningDispatcher(AiScreeningRunRepository aiScreeningRunRepository,
                                  AiSkillMatchRepository aiSkillMatchRepository,
                                  ApplicationRepository applicationRepository,
                                  ApplicationFileRepository applicationFileRepository,
                                  FileStorageService fileStorageService,
                                  MatchingEngine matchingEngine,
                                  Clock clock,
                                  @Value("${app.ai.batch-size:10}") int batchSize,
                                  @Value("${app.ai.concurrency:3}") int concurrency,
                                  @Lazy AiScreeningDispatcher self) {
        this.aiScreeningRunRepository = aiScreeningRunRepository;
        this.aiSkillMatchRepository = aiSkillMatchRepository;
        this.applicationRepository = applicationRepository;
        this.applicationFileRepository = applicationFileRepository;
        this.fileStorageService = fileStorageService;
        this.matchingEngine = matchingEngine;
        this.clock = clock;
        this.batchSize = batchSize;
        this.self = self;
        this.dispatchExecutor = Executors.newFixedThreadPool(Math.max(1, concurrency));
    }

    @PreDestroy
    void shutdownDispatchExecutor() {
        dispatchExecutor.shutdown();
        try {
            if (!dispatchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.poll-interval-ms:5000}")
    public void dispatchPendingRuns() {
        List<AiScreeningRun> batch = aiScreeningRunRepository.findBatchByStatus(
                AiScreeningStatus.PENDING, PageRequest.of(0, batchSize));
        // .join() each future so this method (and therefore the next @Scheduled fixedDelay
        // tick) only returns once the whole batch is done - same "1 poll = drain up to
        // batchSize runs" contract as before, just processed on up to `concurrency` threads
        // at once instead of strictly one after another.
        List<CompletableFuture<Void>> futures = batch.stream()
                .map(run -> CompletableFuture.runAsync(() -> self.dispatchOne(run.getId()), dispatchExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Re-loads the run by id (rather than taking the {@link AiScreeningRun}
     * instance {@link #dispatchPendingRuns} already had) so every entity
     * touched below - the run, its {@code Application}, the Application's
     * primary CV/{@code StoredFile} - is fetched fresh inside THIS method's
     * own transaction/session, never a stale proxy from the poll query's
     * already-closed one.
     */
    @Transactional
    void dispatchOne(Long runId) {
        AiScreeningRun run = aiScreeningRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("AI Screening run {} vanished before it could be dispatched", runId);
            return;
        }

        Application application = run.getApplication();
        Instant now = Instant.now(clock);
        try {
            Optional<ApplicationFile> cvFile = applicationFileRepository
                    .findByApplication_IdAndFileRoleAndPrimaryTrue(application.getId(), ApplicationFileRole.CV);
            if (cvFile.isEmpty()) {
                throw new IllegalStateException("Application no longer has a primary CV attached");
            }

            byte[] cvBytes = fileStorageService.downloadFile(cvFile.get().getFile());
            if (!hasPdfMagicNumber(cvBytes)) {
                // Defense-in-depth: JobApplicationService#validateCv already rejects this at
                // upload time now, but a CV uploaded BEFORE that check existed (or restored from
                // an old backup) can still have a mimeType of "application/pdf" while the actual
                // bytes aren't a real PDF (renamed .doc, corrupted upload...). Catching it here
                // means a clean Vietnamese message instead of Claude's raw validation error
                // ("messages.0.content.0.pdf.source.base64.data: The PDF specified was not
                // valid") - and it saves an API call on a request doomed to fail anyway.
                throw new IllegalStateException(
                        "File CV không phải PDF hợp lệ (bị hỏng hoặc sai định dạng) - ứng viên cần nộp lại CV.");
            }
            JobPosition job = application.getJobPosition();
            String jdText = buildJdText(job);

            run.setModelName(matchingEngine.modelName());
            run.setPromptVersion(matchingEngine.promptVersion());

            MatchAnalysisResult result = matchingEngine.analyze(jdText, cvBytes);

            run.setMatchScore(BigDecimal.valueOf(result.matchScore()));
            run.setSummary(result.summary());
            run.setStatus(AiScreeningStatus.SUCCEEDED);
            run.setCompletedAt(now);
            aiScreeningRunRepository.save(run);

            saveSkillMatches(run, result);

            application.setAiMatchScore(run.getMatchScore());
            applicationRepository.save(application);

            log.info("AI Screening run {} succeeded for application {} (matchScore={})",
                    run.getId(), application.getId(), run.getMatchScore());
        } catch (Exception e) {
            run.setStatus(AiScreeningStatus.FAILED);
            run.setErrorMessage(resolveErrorMessage(e));
            run.setCompletedAt(now);
            aiScreeningRunRepository.save(run);
            log.warn("AI Screening run {} for application {} failed: {}",
                    run.getId(), application.getId(), e.getMessage());
        }
    }

    /**
     * {@code BaseException} (e.g. {@code FileStorageService#downloadFile}'s
     * {@code FILE_NOT_YET_AVAILABLE}) builds its {@code getMessage()} from
     * {@code errorCode.name()} - the raw enum constant, meant to be resolved
     * into human text via {@code MessageSource} ONLY by
     * {@code GlobalExceptionHandler}, at HTTP-response time. This catch block
     * runs on a background scheduler thread with no such resolution step, so
     * calling {@code e.getMessage()} directly leaked things like the literal
     * string "FILE_NOT_YET_AVAILABLE" straight into the Recruiter-facing "Không
     * thể phân tích" message. Special-case it here instead of guessing a
     * message key lookup would behave the same on a non-request thread.
     */
    private static String resolveErrorMessage(Exception e) {
        if (e instanceof BadRequestException bre && bre.getErrorCode() == ErrorCode.FILE_NOT_YET_AVAILABLE) {
            return "CV này được nộp trong lúc Cloud Storage bị mất kết nối nên vẫn đang chờ ở hàng đợi nội bộ, "
                    + "chưa có trên Drive/Dropbox thật để phân tích - cần kết nối lại Cloud Storage (Cài đặt > "
                    + "Tích hợp) rồi yêu cầu ứng viên nộp lại CV.";
        }
        return e.getMessage();
    }

    private void saveSkillMatches(AiScreeningRun run, MatchAnalysisResult result) {
        result.matchedSkills().forEach(skill -> aiSkillMatchRepository.save(AiSkillMatch.builder()
                .run(run)
                .skillName(skill)
                .matchType(AiMatchType.MATCHED)
                .build()));
        result.missingSkills().forEach(skill -> aiSkillMatchRepository.save(AiSkillMatch.builder()
                .run(run)
                .skillName(skill)
                .matchType(AiMatchType.MISSING)
                .build()));
    }

    private static boolean hasPdfMagicNumber(byte[] cvBytes) {
        if (cvBytes == null || cvBytes.length < PDF_MAGIC_NUMBER.length) {
            return false;
        }
        return Arrays.equals(cvBytes, 0, PDF_MAGIC_NUMBER.length, PDF_MAGIC_NUMBER, 0, PDF_MAGIC_NUMBER.length);
    }

    private static String buildJdText(JobPosition job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Chức danh: ").append(job.getTitle()).append('\n');
        if (job.getDescription() != null) {
            sb.append("Mô tả công việc:\n").append(job.getDescription()).append('\n');
        }
        if (job.getRequirements() != null) {
            sb.append("Yêu cầu ứng viên:\n").append(job.getRequirements());
        }
        return sb.toString();
    }
}
