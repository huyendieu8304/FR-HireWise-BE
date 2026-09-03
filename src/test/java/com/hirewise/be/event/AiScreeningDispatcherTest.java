package com.hirewise.be.event;

import com.hirewise.be.ai.MatchAnalysisResult;
import com.hirewise.be.ai.MatchingEngine;
import com.hirewise.be.ai.MatchingEngineException;
import com.hirewise.be.domain.AiScreeningRun;
import com.hirewise.be.domain.AiScreeningStatus;
import com.hirewise.be.domain.AiSkillMatch;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationFileRole;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.repository.AiScreeningRunRepository;
import com.hirewise.be.repository.AiSkillMatchRepository;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-21: actually calling the AI Engine. {@link MatchingEngine} is always
 * mocked here - this suite never makes a real Claude API call, only
 * verifies how {@link AiScreeningDispatcher} reacts to whatever the engine
 * returns/throws.
 */
@ExtendWith(MockitoExtension.class)
class AiScreeningDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");
    private static final UUID APPLICATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AiScreeningRunRepository aiScreeningRunRepository;
    @Mock
    private AiSkillMatchRepository aiSkillMatchRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationFileRepository applicationFileRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private MatchingEngine matchingEngine;

    private AiScreeningDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // self-proxy param only matters for dispatchPendingRuns (not exercised here) - null is fine.
        dispatcher = new AiScreeningDispatcher(aiScreeningRunRepository, aiSkillMatchRepository,
                applicationRepository, applicationFileRepository, fileStorageService, matchingEngine, clock, 10, null);
    }

    private Application application() {
        JobPosition job = JobPosition.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .description("Xây dựng API backend.")
                .requirements("Java, Spring Boot.")
                .build();
        return Application.builder().id(APPLICATION_ID).jobPosition(job).build();
    }

    private AiScreeningRun pendingRun(Application application) {
        return AiScreeningRun.builder()
                .id(1L)
                .application(application)
                .status(AiScreeningStatus.PENDING)
                .createdAt(NOW)
                .build();
    }

    @Test
    void dispatchOne_engineSucceeds_marksRunSucceededAndCachesScoreOnApplication() {
        Application application = application();
        AiScreeningRun run = pendingRun(application);
        StoredFile storedFile = StoredFile.builder().mimeType("application/pdf").build();
        ApplicationFile cvFile = ApplicationFile.builder().file(storedFile).fileRole(ApplicationFileRole.CV).primary(true).build();
        byte[] cvBytes = {1, 2, 3};

        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.of(cvFile));
        when(fileStorageService.downloadFile(storedFile)).thenReturn(cvBytes);
        when(matchingEngine.modelName()).thenReturn("claude-haiku-4-5");
        when(matchingEngine.promptVersion()).thenReturn("v1");
        when(matchingEngine.analyze(any(), any())).thenReturn(
                new MatchAnalysisResult(85, "Phù hợp cao.", List.of("Java"), List.of("Kubernetes")));
        when(aiScreeningRunRepository.findById(run.getId())).thenReturn(Optional.of(run));

        dispatcher.dispatchOne(run.getId());

        assertThat(run.getStatus()).isEqualTo(AiScreeningStatus.SUCCEEDED);
        assertThat(run.getMatchScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(run.getModelName()).isEqualTo("claude-haiku-4-5");
        assertThat(run.getCompletedAt()).isEqualTo(NOW);
        assertThat(application.getAiMatchScore()).isEqualByComparingTo(BigDecimal.valueOf(85));

        verify(aiSkillMatchRepository, times(2)).save(any(AiSkillMatch.class));
        verify(applicationRepository).save(application);
    }

    @Test
    void dispatchOne_engineThrows_marksRunFailedWithoutTouchingApplicationScore() {
        Application application = application();
        AiScreeningRun run = pendingRun(application);
        StoredFile storedFile = StoredFile.builder().mimeType("application/pdf").build();
        ApplicationFile cvFile = ApplicationFile.builder().file(storedFile).fileRole(ApplicationFileRole.CV).primary(true).build();

        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.of(cvFile));
        when(fileStorageService.downloadFile(storedFile)).thenReturn(new byte[]{1});
        when(matchingEngine.modelName()).thenReturn("claude-haiku-4-5");
        when(matchingEngine.promptVersion()).thenReturn("v1");
        when(matchingEngine.analyze(any(), any())).thenThrow(new MatchingEngineException("timeout", null));
        when(aiScreeningRunRepository.findById(run.getId())).thenReturn(Optional.of(run));

        dispatcher.dispatchOne(run.getId());

        assertThat(run.getStatus()).isEqualTo(AiScreeningStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("timeout");
        assertThat(application.getAiMatchScore()).isNull();
        verify(applicationRepository, never()).save(any());
        verify(aiSkillMatchRepository, never()).save(any());
    }

    @Test
    void dispatchOne_cvNoLongerAttached_marksRunFailed() {
        Application application = application();
        AiScreeningRun run = pendingRun(application);
        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.empty());
        when(aiScreeningRunRepository.findById(run.getId())).thenReturn(Optional.of(run));

        dispatcher.dispatchOne(run.getId());

        assertThat(run.getStatus()).isEqualTo(AiScreeningStatus.FAILED);
        verify(matchingEngine, never()).analyze(any(), any());
    }

    @Test
    void dispatchOne_runNoLongerExists_doesNothing() {
        when(aiScreeningRunRepository.findById(999L)).thenReturn(Optional.empty());

        dispatcher.dispatchOne(999L);

        verify(applicationFileRepository, never()).findByApplication_IdAndFileRoleAndPrimaryTrue(any(), any());
        verify(matchingEngine, never()).analyze(any(), any());
    }
}
