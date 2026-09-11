package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ReportScopeResolver;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.dto.response.SlaAlertResponseDto;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * US-MGR-05 (UC-41, SLA Monitoring): the "which Applications are CURRENTLY
 * breaching their Stage's SLA" computation shared by the read-only alert
 * list and {@link SlaBreachWorker} (see {@link SlaBreachWorkerTest} for the
 * email-sending/idempotency side).
 */
@ExtendWith(MockitoExtension.class)
class SlaMonitoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ReportScopeResolver reportScopeResolver;
    @Mock private AccessControlService accessControlService;

    private SlaMonitoringService service;
    private final CurrentUser recruiter = new CurrentUser(1L, "recruiter@test.com", "Recruiter", Set.of("RECRUITER"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new SlaMonitoringService(applicationRepository, reportScopeResolver, accessControlService, clock);
    }

    private PipelineStage stageWithSla(Integer slaHours) {
        return PipelineStage.builder().id(10L).name("Phong van").stageType(StageType.INTERVIEW)
                .terminal(false).active(true).slaHours(slaHours).build();
    }

    private Application application(UUID jobId, PipelineStage stage, Instant lastStageChangedAt) {
        JobPosition job = JobPosition.builder().id(jobId).title("Backend Engineer").build();
        Candidate candidate = Candidate.builder().id(UUID.randomUUID()).fullName("Tran Van A").build();
        return Application.builder()
                .id(UUID.randomUUID()).candidate(candidate).jobPosition(job).currentStage(stage)
                .status(ApplicationStatus.IN_PROGRESS).lastStageChangedAt(lastStageChangedAt).build();
    }

    @Test
    void getSlaAlerts_checksAccess_withSlaViewAlertPermission() {
        when(reportScopeResolver.resolveVisibleJobIds(recruiter)).thenReturn(null);
        when(applicationRepository.findSlaBreachCandidates()).thenReturn(List.of());

        service.getSlaAlerts(recruiter);

        org.mockito.Mockito.verify(accessControlService)
                .checkAccess(recruiter, PermissionCodes.SLA_VIEW_ALERT, ResourceContext.none());
    }

    @Test
    void getSlaAlerts_notYetPastSlaHours_excluded() {
        PipelineStage stage = stageWithSla(24);
        // Only 10h since the stage-dwell started - under the 24h threshold.
        Application application = application(JOB_ID, stage, NOW.minusSeconds(10 * 3600L));
        when(applicationRepository.findSlaBreachCandidates()).thenReturn(List.of(application));
        when(reportScopeResolver.resolveVisibleJobIds(recruiter)).thenReturn(null);

        List<SlaAlertResponseDto> alerts = service.getSlaAlerts(recruiter);

        assertThat(alerts).isEmpty();
    }

    @Test
    void getSlaAlerts_pastSlaHours_included_withHoursOverdueComputed() {
        PipelineStage stage = stageWithSla(24);
        // 30h since the stage-dwell started - 6h past the 24h threshold.
        Application application = application(JOB_ID, stage, NOW.minusSeconds(30 * 3600L));
        when(applicationRepository.findSlaBreachCandidates()).thenReturn(List.of(application));
        when(reportScopeResolver.resolveVisibleJobIds(recruiter)).thenReturn(null);

        List<SlaAlertResponseDto> alerts = service.getSlaAlerts(recruiter);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getApplicationId()).isEqualTo(application.getId());
        assertThat(alerts.get(0).getCandidateName()).isEqualTo("Tran Van A");
        assertThat(alerts.get(0).getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(alerts.get(0).getStageName()).isEqualTo("Phong van");
        assertThat(alerts.get(0).getHoursOverdue()).isEqualTo(30L);
    }

    @Test
    void getSlaAlerts_scopedJobIds_excludesApplicationsOutsideScope() {
        PipelineStage stage = stageWithSla(24);
        Application inScope = application(JOB_ID, stage, NOW.minusSeconds(48 * 3600L));
        Application outOfScope = application(OTHER_JOB_ID, stage, NOW.minusSeconds(48 * 3600L));
        when(applicationRepository.findSlaBreachCandidates()).thenReturn(List.of(inScope, outOfScope));
        when(reportScopeResolver.resolveVisibleJobIds(recruiter)).thenReturn(List.of(JOB_ID));

        List<SlaAlertResponseDto> alerts = service.getSlaAlerts(recruiter);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getApplicationId()).isEqualTo(inScope.getId());
    }

    @Test
    void getSlaAlerts_systemScope_seesEveryJob() {
        PipelineStage stage = stageWithSla(24);
        Application jobA = application(JOB_ID, stage, NOW.minusSeconds(48 * 3600L));
        Application jobB = application(OTHER_JOB_ID, stage, NOW.minusSeconds(48 * 3600L));
        when(applicationRepository.findSlaBreachCandidates()).thenReturn(List.of(jobA, jobB));
        when(reportScopeResolver.resolveVisibleJobIds(recruiter)).thenReturn(null);

        List<SlaAlertResponseDto> alerts = service.getSlaAlerts(recruiter);

        assertThat(alerts).hasSize(2);
    }

    @Test
    void getSlaAlerts_sortedByHoursOverdueDescending_worstFirst() {
        PipelineStage stage = stageWithSla(24);
        Application worst = application(JOB_ID, stage, NOW.minusSeconds(72 * 3600L));
        Application mild = application(JOB_ID, stage, NOW.minusSeconds(30 * 3600L));
        when(applicationRepository.findSlaBreachCandidates()).thenReturn(List.of(mild, worst));
        when(reportScopeResolver.resolveVisibleJobIds(recruiter)).thenReturn(null);

        List<SlaAlertResponseDto> alerts = service.getSlaAlerts(recruiter);

        assertThat(alerts).extracting(SlaAlertResponseDto::getApplicationId)
                .containsExactly(worst.getId(), mild.getId());
    }
}
