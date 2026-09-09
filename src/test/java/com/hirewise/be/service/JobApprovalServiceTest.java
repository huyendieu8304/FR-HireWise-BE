package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.response.InterviewStageScorecardStatusDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobApprovalRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.repository.UserRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UC-14/15: the UC-27 hard gate added to {@code approveJob} - every
 * {@code INTERVIEW}-type Stage of the Job's pipeline must have an
 * {@code ACTIVE} Scorecard configured before it can be Approved.
 */
@ExtendWith(MockitoExtension.class)
class JobApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long DEPARTMENT_ID = 4L;

    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private JobApprovalRepository jobApprovalRepository;
    @Mock private UserAccessScopeRepository userAccessScopeRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private JobStageScorecardService jobStageScorecardService;
    @Mock private AccessControlService accessControlService;
    @Mock private OutboxEventPublisher outboxEventPublisher;

    private JobApprovalService service;
    private final CurrentUser hiringManager = new CurrentUser(1L, "hm@test.com", "Hiring Manager", Set.of("HIRING_MANAGER"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new JobApprovalService(jobPositionRepository, jobApprovalRepository, userAccessScopeRepository,
                userRepository, departmentRepository, pipelineStageRepository, jobStageScorecardService,
                accessControlService, outboxEventPublisher, clock);
    }

    private JobPosition pendingJob() {
        Department department = Department.builder().id(DEPARTMENT_ID).build();
        return JobPosition.builder().id(JOB_ID).department(department).status(JobStatus.PENDING_APPROVAL).build();
    }

    @Test
    void approveJob_someInterviewStageMissingScorecard_throwsBusinessConflictAndNeverMutatesJob() {
        JobPosition job = pendingJob();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobStageScorecardService.getStageStatusForJob(job)).thenReturn(List.of(
                InterviewStageScorecardStatusDto.builder().pipelineStageId(10L).stageName("Phone Screen").configured(true).build(),
                InterviewStageScorecardStatusDto.builder().pipelineStageId(20L).stageName("Technical Interview").configured(false).build()));

        assertThatThrownBy(() -> service.approveJob(JOB_ID, hiringManager))
                .isInstanceOf(BusinessConflictException.class);

        // Fail-fast: status never mutated, no approval trail row written, no email enqueued.
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING_APPROVAL);
        org.mockito.Mockito.verify(jobPositionRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(jobApprovalRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(outboxEventPublisher, org.mockito.Mockito.never()).publish(any(), any());
    }

    @Test
    void approveJob_everyInterviewStageConfigured_approvesSuccessfully() {
        JobPosition job = pendingJob();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobStageScorecardService.getStageStatusForJob(job)).thenReturn(List.of(
                InterviewStageScorecardStatusDto.builder().pipelineStageId(10L).stageName("Phone Screen").configured(true).build(),
                InterviewStageScorecardStatusDto.builder().pipelineStageId(20L).stageName("Technical Interview").configured(true).build()));

        service.approveJob(JOB_ID, hiringManager);

        assertThat(job.getStatus()).isEqualTo(JobStatus.APPROVED);
        org.mockito.Mockito.verify(jobApprovalRepository).save(any());
    }

    @Test
    void approveJob_noInterviewStageAtAll_gateIsANoOp() {
        JobPosition job = pendingJob();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobStageScorecardService.getStageStatusForJob(job)).thenReturn(List.of());

        service.approveJob(JOB_ID, hiringManager);

        assertThat(job.getStatus()).isEqualTo(JobStatus.APPROVED);
    }
}
