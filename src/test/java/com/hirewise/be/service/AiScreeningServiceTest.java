package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.AiMatchType;
import com.hirewise.be.domain.AiScreeningRun;
import com.hirewise.be.domain.AiScreeningStatus;
import com.hirewise.be.domain.AiSkillMatch;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationFile;
import com.hirewise.be.domain.ApplicationFileRole;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.dto.response.AiScreeningResultResponseDto;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.AiScreeningRunRepository;
import com.hirewise.be.repository.AiSkillMatchRepository;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-21: queues/reads AI Screening Runs. Never calls the real Claude API -
 * {@code ai.MatchingEngine} isn't even a dependency of this service (see
 * {@code event.AiScreeningDispatcher} instead, which owns the actual call).
 */
@ExtendWith(MockitoExtension.class)
class AiScreeningServiceTest {

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
    private AccessControlService accessControlService;

    private AiScreeningService aiScreeningService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        aiScreeningService = new AiScreeningService(
                aiScreeningRunRepository, aiSkillMatchRepository, applicationRepository,
                applicationFileRepository, accessControlService, clock);
    }

    private Application application() {
        Department department = Department.builder().id(4L).build();
        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).department(department).build();
        return Application.builder().id(APPLICATION_ID).jobPosition(job).build();
    }

    private ApplicationFile cvFile(String mimeType) {
        StoredFile storedFile = StoredFile.builder().mimeType(mimeType).build();
        return ApplicationFile.builder().file(storedFile).fileRole(ApplicationFileRole.CV).primary(true).build();
    }

    @Test
    void enqueueRun_noCvAttached_savesImmediatelyFailedRun() {
        Application application = application();
        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.empty());

        aiScreeningService.enqueueRun(application);

        ArgumentCaptor<AiScreeningRun> captor = ArgumentCaptor.forClass(AiScreeningRun.class);
        verify(aiScreeningRunRepository).save(captor.capture());
        AiScreeningRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AiScreeningStatus.FAILED);
        assertThat(saved.getModelName()).isNull();
        assertThat(saved.getErrorMessage()).contains("Chưa có CV");
        assertThat(saved.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void enqueueRun_cvNotPdf_savesImmediatelyFailedRun() {
        Application application = application();
        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.of(cvFile("application/msword")));

        aiScreeningService.enqueueRun(application);

        ArgumentCaptor<AiScreeningRun> captor = ArgumentCaptor.forClass(AiScreeningRun.class);
        verify(aiScreeningRunRepository).save(captor.capture());
        AiScreeningRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AiScreeningStatus.FAILED);
        assertThat(saved.getErrorMessage()).contains("chưa được hỗ trợ");
    }

    @Test
    void enqueueRun_cvIsPdf_savesPendingRun() {
        Application application = application();
        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.of(cvFile("application/pdf")));
        when(aiScreeningRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        aiScreeningService.enqueueRun(application);

        ArgumentCaptor<AiScreeningRun> captor = ArgumentCaptor.forClass(AiScreeningRun.class);
        verify(aiScreeningRunRepository).save(captor.capture());
        AiScreeningRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AiScreeningStatus.PENDING);
        assertThat(saved.getModelName()).isNull(); // set later by the dispatcher, not here
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void runManual_unknownApplication_throwsResourceNotFound() {
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());
        CurrentUser currentUser = new CurrentUser(1L, "recruiter@test.com", "Recruiter", Set.of());

        assertThatThrownBy(() -> aiScreeningService.runManual(APPLICATION_ID, currentUser))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(applicationFileRepository, never()).findByApplication_IdAndFileRoleAndPrimaryTrue(any(), any());
    }

    @Test
    void runManual_valid_checksAccessThenQueuesNewRun() {
        Application application = application();
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(applicationFileRepository.findByApplication_IdAndFileRoleAndPrimaryTrue(APPLICATION_ID, ApplicationFileRole.CV))
                .thenReturn(Optional.of(cvFile("application/pdf")));
        when(aiScreeningRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CurrentUser currentUser = new CurrentUser(1L, "recruiter@test.com", "Recruiter", Set.of());

        aiScreeningService.runManual(APPLICATION_ID, currentUser);

        verify(accessControlService).checkAccess(eq(currentUser), eq(PermissionCodes.AI_VIEW), any(ResourceContext.class));
        verify(aiScreeningRunRepository).save(any(AiScreeningRun.class));
    }

    @Test
    void getLatestResult_noRunYet_returnsNull() {
        Application application = application();
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(aiScreeningRunRepository.findFirstByApplication_IdOrderByCreatedAtDesc(APPLICATION_ID))
                .thenReturn(Optional.empty());
        CurrentUser currentUser = new CurrentUser(1L, "recruiter@test.com", "Recruiter", Set.of());

        AiScreeningResultResponseDto result = aiScreeningService.getLatestResult(APPLICATION_ID, currentUser);

        assertThat(result).isNull();
    }

    @Test
    void getLatestResult_succeededRun_splitsMatchedAndMissingSkills() {
        Application application = application();
        AiScreeningRun run = AiScreeningRun.builder()
                .id(99L)
                .application(application)
                .status(AiScreeningStatus.SUCCEEDED)
                .matchScore(new BigDecimal("85.00"))
                .summary("Ứng viên phù hợp cao.")
                .modelName("claude-haiku-4-5")
                .promptVersion("v1")
                .createdAt(NOW)
                .completedAt(NOW)
                .build();
        List<AiSkillMatch> skillMatches = List.of(
                AiSkillMatch.builder().run(run).skillName("Java").matchType(AiMatchType.MATCHED).build(),
                AiSkillMatch.builder().run(run).skillName("Kubernetes").matchType(AiMatchType.MISSING).build());

        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(aiScreeningRunRepository.findFirstByApplication_IdOrderByCreatedAtDesc(APPLICATION_ID))
                .thenReturn(Optional.of(run));
        when(aiSkillMatchRepository.findByRun_Id(99L)).thenReturn(skillMatches);
        CurrentUser currentUser = new CurrentUser(1L, "recruiter@test.com", "Recruiter", Set.of());

        AiScreeningResultResponseDto result = aiScreeningService.getLatestResult(APPLICATION_ID, currentUser);

        assertThat(result.getStatus()).isEqualTo(AiScreeningStatus.SUCCEEDED);
        assertThat(result.getMatchScore()).isEqualByComparingTo("85.00");
        assertThat(result.getMatchedSkills()).containsExactly("Java");
        assertThat(result.getMissingSkills()).containsExactly("Kubernetes");
    }
}
