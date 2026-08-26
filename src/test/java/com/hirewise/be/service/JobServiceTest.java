package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.EmploymentType;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.JobPositionRequestDto;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.repository.UserRepository;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-12: Draft/edit a Job Position.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final Long DEPARTMENT_ID = 4L;
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private JobPositionRepository jobPositionRepository;
    @Mock
    private UserAccessScopeRepository userAccessScopeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;

    private JobService jobService;
    private CurrentUser recruiter;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        jobService = new JobService(jobPositionRepository, userAccessScopeRepository,
                departmentRepository, userRepository, accessControlService, fixedClock);
        recruiter = new CurrentUser(7L, "recruiter@hirewise.com", "Recruiter One", Set.of("RECRUITER"));
    }

    private JobPositionRequestDto validRequest() {
        return new JobPositionRequestDto(
                "Backend Engineer", DEPARTMENT_ID, EmploymentType.FULL_TIME,
                new BigDecimal("1000"), new BigDecimal("2000"), 2,
                LocalDate.parse("2026-12-31"), "Ho Chi Minh",
                "Build APIs", "3 years experience", "13th month salary");
    }

    private JobPosition draftJob(JobStatus status, Long departmentId) {
        Department department = departmentId != null
                ? Department.builder().id(departmentId).name("Engineering").build() : null;
        return JobPosition.builder()
                .id(JOB_ID)
                .title("Old Title")
                .department(department)
                .openings(1)
                .status(status)
                .createdByUserId(recruiter.userId())
                .recruiter(User.builder().id(recruiter.userId()).fullName("Recruiter One").build())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Test
    void createJob_savesAsDraftSelfAssignedToRecruiter() {
        Department department = Department.builder().id(DEPARTMENT_ID).name("Engineering").build();
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(userRepository.getReferenceById(recruiter.userId()))
                .thenReturn(User.builder().id(recruiter.userId()).fullName("Recruiter One").build());

        JobDetailResponseDto response = jobService.createJob(validRequest(), recruiter);

        assertThat(response.getTitle()).isEqualTo("Backend Engineer");
        assertThat(response.getStatus()).isEqualTo(JobStatus.DRAFT);
        assertThat(response.getRecruiterName()).isEqualTo("Recruiter One");
        verify(accessControlService).checkAccess(recruiter, PermissionCodes.JOB_CREATE,
                ResourceContext.department(DEPARTMENT_ID));

        ArgumentCaptor<JobPosition> captor = ArgumentCaptor.forClass(JobPosition.class);
        verify(jobPositionRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedByUserId()).isEqualTo(recruiter.userId());
    }

    @Test
    void createJob_unknownDepartment_throwsResourceNotFound() {
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.createJob(validRequest(), recruiter))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
        verify(jobPositionRepository, never()).save(any());
    }

    @Test
    void createJob_salaryMinGreaterThanMax_throwsBadRequest_EX02() {
        when(departmentRepository.findById(DEPARTMENT_ID))
                .thenReturn(Optional.of(Department.builder().id(DEPARTMENT_ID).build()));
        JobPositionRequestDto request = validRequest();
        request.setSalaryMin(new BigDecimal("5000"));
        request.setSalaryMax(new BigDecimal("2000"));

        assertThatThrownBy(() -> jobService.createJob(request, recruiter))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_SALARY_RANGE_INVALID);
        verify(jobPositionRepository, never()).save(any());
    }

    @Test
    void createJob_deadlineInPast_throwsBadRequest_EX03() {
        when(departmentRepository.findById(DEPARTMENT_ID))
                .thenReturn(Optional.of(Department.builder().id(DEPARTMENT_ID).build()));
        JobPositionRequestDto request = validRequest();
        request.setApplicationDeadline(LocalDate.parse("2020-01-01"));

        assertThatThrownBy(() -> jobService.createJob(request, recruiter))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_DEADLINE_IN_PAST);
        verify(jobPositionRepository, never()).save(any());
    }

    @Test
    void createJob_deniedWhenNoRoleGrantsPermission() {
        doThrow(new PermissionDeniedException()).when(accessControlService)
                .checkAccess(eq(recruiter), eq(PermissionCodes.JOB_CREATE), any());

        assertThatThrownBy(() -> jobService.createJob(validRequest(), recruiter))
                .isInstanceOf(PermissionDeniedException.class);
        verify(jobPositionRepository, never()).save(any());
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void updateDraftJob_unknownJob_throwsResourceNotFound() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateDraftJob(JOB_ID, validRequest(), recruiter))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_POSITION_NOT_FOUND);
        verify(accessControlService, never()).checkAccess(any(), any(), any());
    }

    @Test
    void updateDraftJob_publishedStatus_throwsBusinessConflict_BR_JOB_04() {
        when(jobPositionRepository.findById(JOB_ID))
                .thenReturn(Optional.of(draftJob(JobStatus.PUBLISHED, DEPARTMENT_ID)));

        assertThatThrownBy(() -> jobService.updateDraftJob(JOB_ID, validRequest(), recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_POSITION_NOT_EDITABLE);
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void updateDraftJob_draftStatus_updatesFieldsSuccessfully() {
        when(jobPositionRepository.findById(JOB_ID))
                .thenReturn(Optional.of(draftJob(JobStatus.DRAFT, DEPARTMENT_ID)));
        when(departmentRepository.findById(DEPARTMENT_ID))
                .thenReturn(Optional.of(Department.builder().id(DEPARTMENT_ID).name("Engineering").build()));

        JobDetailResponseDto response = jobService.updateDraftJob(JOB_ID, validRequest(), recruiter);

        assertThat(response.getTitle()).isEqualTo("Backend Engineer");
        verify(accessControlService).checkAccess(recruiter, PermissionCodes.JOB_EDIT,
                ResourceContext.department(DEPARTMENT_ID));
    }

    @Test
    void updateDraftJob_rejectedStatus_stillEditable_BR_JOB_04() {
        when(jobPositionRepository.findById(JOB_ID))
                .thenReturn(Optional.of(draftJob(JobStatus.REJECTED, DEPARTMENT_ID)));
        when(departmentRepository.findById(DEPARTMENT_ID))
                .thenReturn(Optional.of(Department.builder().id(DEPARTMENT_ID).name("Engineering").build()));

        JobDetailResponseDto response = jobService.updateDraftJob(JOB_ID, validRequest(), recruiter);

        assertThat(response.getTitle()).isEqualTo("Backend Engineer");
    }

    @Test
    void updateDraftJob_salaryInvalid_throwsBadRequest_beforeSaving() {
        when(jobPositionRepository.findById(JOB_ID))
                .thenReturn(Optional.of(draftJob(JobStatus.DRAFT, DEPARTMENT_ID)));
        when(departmentRepository.findById(DEPARTMENT_ID))
                .thenReturn(Optional.of(Department.builder().id(DEPARTMENT_ID).build()));
        JobPositionRequestDto request = validRequest();
        request.setSalaryMin(new BigDecimal("9999"));
        request.setSalaryMax(new BigDecimal("1"));

        assertThatThrownBy(() -> jobService.updateDraftJob(JOB_ID, request, recruiter))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_SALARY_RANGE_INVALID);
        verify(jobPositionRepository, never()).save(any());
    }
}
