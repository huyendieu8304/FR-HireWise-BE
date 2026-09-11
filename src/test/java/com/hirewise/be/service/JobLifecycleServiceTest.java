package com.hirewise.be.service;

import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UC-45 (dang tin len Job Board) va UC-44 (Tam dung / Dong / Mo lai).
 *
 * <p>Bao phu day du ma tran chuyen trang thai cua LV-03: moi transition duoc
 * kiem tra ca nhanh hop le lan nhanh bi chan, dac biet la BR-JOB-05 (CLOSED
 * la terminal, khong the mo lai) va viec Mo lai KHONG tao them ban ghi phe
 * duyet nao. Audit trail duoc kiem bang ArgumentCaptor vi ly do Tam dung/Dong
 * chi ton tai o do, khong co cot rieng tren job_positions.</p>
 *
 * <p>Khong test authorization o day: ca 4 entry point deu nam sau
 * {@code @RequiresOwnership} tren JobController, da co OwnershipAspectTest va
 * JobPositionOwnershipResolverTest phu trach.</p>
 */
@ExtendWith(MockitoExtension.class)
class JobLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T03:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-26T00:00:00Z");
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private JobPositionRepository jobPositionRepository;
    @Mock
    private AuditLogService auditLogService;

    private JobLifecycleService service;
    private CurrentUser recruiter;

    @BeforeEach
    void setUp() {
        service = new JobLifecycleService(
                jobPositionRepository, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));
        recruiter = new CurrentUser(7L, "recruiter@hirewise.com", "Recruiter One", Set.of("RECRUITER"));
    }

    private JobPosition jobWith(JobStatus status) {
        return JobPosition.builder()
                .id(JOB_ID)
                .title("Backend Engineer")
                .department(Department.builder().id(4L).name("Engineering").build())
                .openings(2)
                .status(status)
                .createdByUserId(recruiter.userId())
                .recruiter(User.builder().id(recruiter.userId()).fullName("Recruiter One").build())
                .createdAt(EARLIER)
                .updatedAt(EARLIER)
                .build();
    }

    private JobPosition given(JobStatus status) {
        JobPosition job = jobWith(status);
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        return job;
    }

    private JobPosition savedJob() {
        ArgumentCaptor<JobPosition> captor = ArgumentCaptor.forClass(JobPosition.class);
        verify(jobPositionRepository).save(captor.capture());
        return captor.getValue();
    }

    private String capturedAfterJson(String expectedAction) {
        ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq(recruiter.userId()), eq(expectedAction), eq("job_positions"),
                eq(JOB_ID.toString()), anyString(), after.capture());
        return after.getValue();
    }

    // ---------- UC-45: publish ----------

    @Test
    void publishFromApprovedGoesLiveAndStampsUpdatedAt() {
        given(JobStatus.APPROVED);

        JobDetailResponseDto response = service.publish(JOB_ID, recruiter);

        assertThat(response.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(savedJob().getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(savedJob().getUpdatedAt()).isEqualTo(NOW);
        verify(auditLogService).record(eq(recruiter.userId()), eq("JOB_PUBLISHED"), eq("job_positions"),
                eq(JOB_ID.toString()), anyString(), anyString());
    }

    @Test
    void publishFromPendingApprovalIsRejectedWithItsOwnMessage() {
        given(JobStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> service.publish(JOB_ID, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_POSITION_NOT_PUBLISHABLE);

        verify(jobPositionRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    /** Publish twice must not be a silent no-op - it has to say the job is already live. */
    @Test
    void publishAnAlreadyPublishedJobIsRejected() {
        given(JobStatus.PUBLISHED);

        assertThatThrownBy(() -> service.publish(JOB_ID, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_POSITION_NOT_PUBLISHABLE);
    }

    // ---------- UC-44: pause ----------

    @Test
    void pauseFromPublishedHidesTheJobAndRecordsTheReason() {
        given(JobStatus.PUBLISHED);

        JobDetailResponseDto response = service.pause(JOB_ID, "Da du ho so, dang xu ly backlog", recruiter);

        assertThat(response.getStatus()).isEqualTo(JobStatus.PAUSED);
        assertThat(savedJob().getStatus()).isEqualTo(JobStatus.PAUSED);
        assertThat(capturedAfterJson("JOB_PAUSED"))
                .contains("\"status\":\"PAUSED\"")
                .contains("Da du ho so");
    }

    @Test
    void pauseWithoutAReasonOmitsItFromTheAuditSnapshot() {
        given(JobStatus.PUBLISHED);

        service.pause(JOB_ID, null, recruiter);

        assertThat(capturedAfterJson("JOB_PAUSED")).isEqualTo("{\"status\":\"PAUSED\"}");
    }

    @Test
    void pauseWithAQuotedReasonProducesValidJson() {
        given(JobStatus.PUBLISHED);

        service.pause(JOB_ID, "Ly do \"khan cap\"\nxuong dong", recruiter);

        assertThat(capturedAfterJson("JOB_PAUSED"))
                .isEqualTo("{\"status\":\"PAUSED\",\"reason\":\"Ly do \\\"khan cap\\\"\\nxuong dong\"}");
    }

    @Test
    void pauseFromDraftIsRejected() {
        given(JobStatus.DRAFT);

        assertThatThrownBy(() -> service.pause(JOB_ID, null, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_STATUS_TRANSITION_NOT_ALLOWED);

        verify(jobPositionRepository, never()).save(any());
    }

    @Test
    void pauseAnAlreadyPausedJobIsRejected() {
        given(JobStatus.PAUSED);

        assertThatThrownBy(() -> service.pause(JOB_ID, null, recruiter))
                .isInstanceOf(BusinessConflictException.class);
    }

    // ---------- UC-44: close ----------

    @Test
    void closeFromPublishedIsAllowed() {
        given(JobStatus.PUBLISHED);

        assertThat(service.close(JOB_ID, "Da tuyen du chi tieu", recruiter).getStatus())
                .isEqualTo(JobStatus.CLOSED);
        assertThat(capturedAfterJson("JOB_CLOSED")).contains("Da tuyen du chi tieu");
    }

    @Test
    void closeFromPausedIsAllowed() {
        given(JobStatus.PAUSED);

        assertThat(service.close(JOB_ID, null, recruiter).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(savedJob().getStatus()).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    void closeFromDraftIsRejected() {
        given(JobStatus.DRAFT);

        assertThatThrownBy(() -> service.close(JOB_ID, null, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_STATUS_TRANSITION_NOT_ALLOWED);
    }

    @Test
    void closeAnAlreadyClosedJobIsRejected() {
        given(JobStatus.CLOSED);

        assertThatThrownBy(() -> service.close(JOB_ID, null, recruiter))
                .isInstanceOf(BusinessConflictException.class);

        verify(jobPositionRepository, never()).save(any());
    }

    // ---------- UC-44 AF-01: resume ----------

    @Test
    void resumeFromPausedGoesStraightBackToPublished() {
        given(JobStatus.PAUSED);

        JobDetailResponseDto response = service.resume(JOB_ID, recruiter);

        assertThat(response.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(savedJob().getUpdatedAt()).isEqualTo(NOW);
        verify(auditLogService).record(eq(recruiter.userId()), eq("JOB_RESUMED"), eq("job_positions"),
                eq(JOB_ID.toString()), anyString(), anyString());
    }

    /** BR-JOB-05: Closed is terminal - there is no way back to Published. */
    @Test
    void resumeAClosedJobIsRejected() {
        given(JobStatus.CLOSED);

        assertThatThrownBy(() -> service.resume(JOB_ID, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_STATUS_TRANSITION_NOT_ALLOWED);

        verify(jobPositionRepository, never()).save(any());
    }

    @Test
    void resumeAPublishedJobIsRejected() {
        given(JobStatus.PUBLISHED);

        assertThatThrownBy(() -> service.resume(JOB_ID, recruiter))
                .isInstanceOf(BusinessConflictException.class);
    }

    // ---------- shared ----------

    @Test
    void unknownJobIdThrowsResourceNotFoundOnEveryTransition() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(JOB_ID, recruiter)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.pause(JOB_ID, null, recruiter)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.close(JOB_ID, null, recruiter)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resume(JOB_ID, recruiter)).isInstanceOf(ResourceNotFoundException.class);

        verify(auditLogService, never()).record(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }
}
