package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-MGR-05 (UC-41, SLA Monitoring): {@link SlaBreachWorker}'s grouping,
 * idempotency and Hiring-Manager-missing edge case. {@link SlaMonitoringService}
 * itself is mocked here - its own breach-detection logic is
 * {@link SlaMonitoringServiceTest}'s job.
 */
@ExtendWith(MockitoExtension.class)
class SlaBreachWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock private ApplicationRepository applicationRepository;
    @Mock private SlaMonitoringService slaMonitoringService;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    private SlaBreachWorker worker;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        worker = new SlaBreachWorker(applicationRepository, slaMonitoringService, outboxEventPublisher,
                clock, "http://localhost:5173");
    }

    private PipelineStage stage(Long id, String name) {
        return PipelineStage.builder().id(id).name(name).stageType(StageType.INTERVIEW)
                .terminal(false).active(true).slaHours(24).build();
    }

    private JobPosition jobWithManager(UUID id, User manager) {
        return JobPosition.builder().id(id).title("Backend Engineer").hiringManager(manager).build();
    }

    private Application breachingApplication(JobPosition job, PipelineStage stage, String candidateName,
                                              Instant slaAlertSentAt) {
        Candidate candidate = Candidate.builder().id(UUID.randomUUID()).fullName(candidateName).build();
        return Application.builder()
                .id(UUID.randomUUID()).candidate(candidate).jobPosition(job).currentStage(stage)
                .status(ApplicationStatus.IN_PROGRESS).lastStageChangedAt(NOW.minusSeconds(48 * 3600L))
                .slaAlertSentAt(slaAlertSentAt).build();
    }

    @Test
    void sendBreachAlerts_noBreaches_publishesNothing() {
        when(slaMonitoringService.findAllCurrentBreaches()).thenReturn(List.of());

        worker.sendBreachAlerts();

        verify(outboxEventPublisher, never()).publish(any(), any());
        verify(applicationRepository, never()).saveAll(any());
    }

    @Test
    void sendBreachAlerts_alreadyAlertedForThisDwell_skipped() {
        User manager = User.builder().id(1L).email("hm@test.com").fullName("Hiring Manager").build();
        JobPosition job = jobWithManager(UUID.randomUUID(), manager);
        PipelineStage stage = stage(10L, "Phong van");
        Application alreadyAlerted = breachingApplication(job, stage, "Ung vien A", NOW.minusSeconds(3600));
        when(slaMonitoringService.findAllCurrentBreaches())
                .thenReturn(List.of(new SlaMonitoringService.Breach(alreadyAlerted, stage, 48L)));

        worker.sendBreachAlerts();

        verify(outboxEventPublisher, never()).publish(any(), any());
        verify(applicationRepository, never()).saveAll(any());
    }

    @Test
    void sendBreachAlerts_groupsByJobAndStage_sendsOneEmailPerGroupWithCorrectPayload() {
        User manager = User.builder().id(1L).email("hm@test.com").fullName("Hiring Manager").build();
        UUID jobId = UUID.randomUUID();
        JobPosition job = jobWithManager(jobId, manager);
        PipelineStage stage = stage(10L, "Phong van chuyen mon");
        Application candidateA = breachingApplication(job, stage, "Nguyen Van A", null);
        Application candidateB = breachingApplication(job, stage, "Tran Thi B", null);
        when(slaMonitoringService.findAllCurrentBreaches()).thenReturn(List.of(
                new SlaMonitoringService.Breach(candidateA, stage, 30L),
                new SlaMonitoringService.Breach(candidateB, stage, 50L)));

        worker.sendBreachAlerts();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher, times(1))
                .publish(eq(OutboxEventType.SLA_BREACH_ALERT_EMAIL), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("email", "hm@test.com");
        assertThat(payload).containsEntry("managerName", "Hiring Manager");
        assertThat(payload).containsEntry("n", 2);
        assertThat(payload).containsEntry("stageName", "Phong van chuyen mon");
        assertThat(payload).containsEntry("jobTitle", "Backend Engineer");
        assertThat(payload).containsEntry("dashboardLink", "http://localhost:5173/dashboard");
        assertThat((String) payload.get("breachList"))
                .contains("Nguyen Van A").contains("Tran Thi B");
    }

    @Test
    void sendBreachAlerts_marksEveryBreachingApplicationAlerted() {
        User manager = User.builder().id(1L).email("hm@test.com").fullName("HM").build();
        JobPosition job = jobWithManager(UUID.randomUUID(), manager);
        PipelineStage stage = stage(10L, "Phong van");
        Application candidateA = breachingApplication(job, stage, "A", null);
        when(slaMonitoringService.findAllCurrentBreaches())
                .thenReturn(List.of(new SlaMonitoringService.Breach(candidateA, stage, 30L)));

        worker.sendBreachAlerts();

        assertThat(candidateA.getSlaAlertSentAt()).isEqualTo(NOW);
        verify(applicationRepository).saveAll(List.of(candidateA));
    }

    @Test
    void sendBreachAlerts_jobWithNoHiringManager_skipsThatGroupButStillAlertsOthers() {
        JobPosition jobWithoutManager = JobPosition.builder().id(UUID.randomUUID()).title("No Manager Job").build();
        User manager = User.builder().id(2L).email("hm2@test.com").fullName("HM Two").build();
        JobPosition jobWithManager = jobWithManager(UUID.randomUUID(), manager);
        PipelineStage stage = stage(10L, "Phong van");
        Application unmanaged = breachingApplication(jobWithoutManager, stage, "Unmanaged Candidate", null);
        Application managed = breachingApplication(jobWithManager, stage, "Managed Candidate", null);
        when(slaMonitoringService.findAllCurrentBreaches()).thenReturn(List.of(
                new SlaMonitoringService.Breach(unmanaged, stage, 30L),
                new SlaMonitoringService.Breach(managed, stage, 30L)));

        worker.sendBreachAlerts();

        // Only 1 email (for the managed Job) - the unmanaged group has nobody to send to.
        verify(outboxEventPublisher, times(1)).publish(any(), any());
        // Both Applications are still marked alerted either way - re-polling forever for a
        // Job that will never have anyone to notify would be pointless noise.
        verify(applicationRepository).saveAll(List.of(unmanaged, managed));
    }
}
