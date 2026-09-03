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
import com.hirewise.be.repository.AiScreeningRunRepository;
import com.hirewise.be.repository.AiSkillMatchRepository;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.service.FileStorageService;
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
import java.util.List;
import java.util.Optional;

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
 */
@Slf4j
@Component
public class AiScreeningDispatcher {

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

    public AiScreeningDispatcher(AiScreeningRunRepository aiScreeningRunRepository,
                                  AiSkillMatchRepository aiSkillMatchRepository,
                                  ApplicationRepository applicationRepository,
                                  ApplicationFileRepository applicationFileRepository,
                                  FileStorageService fileStorageService,
                                  MatchingEngine matchingEngine,
                                  Clock clock,
                                  @Value("${app.ai.batch-size:10}") int batchSize,
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
    }

    @Scheduled(fixedDelayString = "${app.ai.poll-interval-ms:5000}")
    public void dispatchPendingRuns() {
        List<AiScreeningRun> batch = aiScreeningRunRepository.findBatchByStatus(
                AiScreeningStatus.PENDING, PageRequest.of(0, batchSize));
        for (AiScreeningRun run : batch) {
            self.dispatchOne(run.getId());
        }
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
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(now);
            aiScreeningRunRepository.save(run);
            log.warn("AI Screening run {} for application {} failed: {}",
                    run.getId(), application.getId(), e.getMessage());
        }
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
