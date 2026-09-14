package com.hirewise.be.service;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.request.SubmitApplicationRequestDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationFileRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.CandidateRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UC-17: gate "Job con nhan ho so" truoc khi tao Candidate/Application.
 * Job qua han nop ho so (application_deadline) phai bi chan y nhu Job khong
 * con Published - khong tao Candidate, khong upload CV.
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    /** 18:00 UTC ngay 14/09 = 01:00 ngay 15/09 o Viet Nam. */
    private static final Instant NOW = Instant.parse("2026-09-14T18:00:00Z");
    private static final LocalDate TODAY_VN = LocalDate.of(2026, 9, 15);
    private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private JobPositionRepository jobPositionRepository;
    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationFileRepository applicationFileRepository;
    @Mock
    private ApplicationStageHistoryRepository applicationStageHistoryRepository;
    @Mock
    private PipelineStageRepository pipelineStageRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private AuditLogService auditLogService;

    private JobApplicationService jobApplicationService;

    @BeforeEach
    void setUp() {
        jobApplicationService = new JobApplicationService(
                jobPositionRepository, candidateRepository, applicationRepository, applicationFileRepository,
                applicationStageHistoryRepository, pipelineStageRepository, fileStorageService,
                outboxEventPublisher, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SubmitApplicationRequestDto request() {
        SubmitApplicationRequestDto request = new SubmitApplicationRequestDto();
        request.setFullName("Nguyen Van An");
        request.setEmail("an.nguyen@test.com");
        request.setPhone("0901234567");
        return request;
    }

    @Test
    void deadlineToday_usesVietnamDateNotUtcDate() {
        assertThat(JobPosition.deadlineToday(Clock.fixed(NOW, ZoneOffset.UTC))).isEqualTo(TODAY_VN);
    }

    @Test
    void apply_jobPastDeadlineOrNotPublished_throwsNotFoundWithoutTouchingCandidateOrStorage() {
        when(jobPositionRepository.findOpenForApplications(JOB_ID, TODAY_VN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.apply(JOB_ID, request(), null))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(candidateRepository, fileStorageService, outboxEventPublisher);
        verify(applicationRepository, never()).save(any());
    }

    /** Job con han (gate tra ve Job) thi di tiep sang validate CV - chung minh gate khong chan nham. */
    @Test
    void apply_jobStillOpen_passesDeadlineGateAndContinuesToCvValidation() {
        JobPosition job = JobPosition.builder()
                .id(JOB_ID)
                .status(JobStatus.PUBLISHED)
                .applicationDeadline(TODAY_VN)
                .openings(1)
                .build();
        when(jobPositionRepository.findOpenForApplications(JOB_ID, TODAY_VN)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobApplicationService.apply(JOB_ID, request(), null))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(candidateRepository, fileStorageService);
    }
}
