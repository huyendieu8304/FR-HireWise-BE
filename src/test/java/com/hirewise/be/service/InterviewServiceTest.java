package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewParticipant;
import com.hirewise.be.domain.InterviewStatus;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.PipelineTemplate;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.domain.InterviewBookingRequest;
import com.hirewise.be.domain.InterviewBookingRequestStatus;
import com.hirewise.be.domain.InterviewBookingSlot;
import com.hirewise.be.domain.InterviewBookingSlotStatus;
import com.hirewise.be.dto.request.ConfirmBookingSlotRequestDto;
import com.hirewise.be.dto.request.ScheduleInterviewRequestDto;
import com.hirewise.be.dto.request.SendBookingLinkRequestDto;
import com.hirewise.be.dto.response.BookingConfirmResponseDto;
import com.hirewise.be.dto.response.BookingPageResponseDto;
import com.hirewise.be.dto.response.BookingRequestResponseDto;
import com.hirewise.be.dto.response.InterviewerOptionDto;
import com.hirewise.be.dto.response.ScheduleInterviewResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.InterviewBookingRequestRepository;
import com.hirewise.be.repository.InterviewBookingSlotRepository;
import com.hirewise.be.repository.InterviewParticipantRepository;
import com.hirewise.be.repository.InterviewRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    InterviewRepository interviewRepository;

    @Mock
    InterviewParticipantRepository interviewParticipantRepository;

    @Mock
    ApplicationRepository applicationRepository;

    @Mock
    ApplicationStageHistoryRepository applicationStageHistoryRepository;

    @Mock
    PipelineStageRepository pipelineStageRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    OutboxEventPublisher outboxEventPublisher;

    @Mock
    AccessControlService accessControlService;

    @Mock
    CalendarIntegrationService calendarIntegrationService;

    @Mock
    InterviewBookingRequestRepository interviewBookingRequestRepository;

    @Mock
    InterviewBookingSlotRepository interviewBookingSlotRepository;

    InterviewService interviewService;

    Clock fixedClock;
    Instant fixedInstant;
    CurrentUser recruiterUser;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-09-03T10:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        interviewService = new InterviewService(
                interviewRepository,
                interviewParticipantRepository,
                applicationRepository,
                applicationStageHistoryRepository,
                pipelineStageRepository,
                userRepository,
                outboxEventPublisher,
                accessControlService,
                calendarIntegrationService,
                fixedClock,
                interviewBookingRequestRepository,
                interviewBookingSlotRepository,
                "http://localhost:5173/booking"
        );
        recruiterUser = new CurrentUser(100L, "recruiter@hirewise.vn", "Recruiter A", Set.of("RECRUITER"));
    }

    @Test
    @DisplayName("Schedules interview successfully, moves stage, assigns interviewers and publishes outbox events")
    void scheduleInterview_success() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage screeningStage = PipelineStage.builder()
                .id(10L)
                .name("Screening")
                .stageType(StageType.SCREENING)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();
        PipelineStage interviewStage = PipelineStage.builder()
                .id(20L)
                .name("Interview Round 1")
                .stageType(StageType.INTERVIEW)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();

        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).title("Backend Dev").pipelineTemplate(template).build();
        Candidate candidate = Candidate.builder().id(UUID.randomUUID()).fullName("Tran Van B").primaryEmail("tranvanb@gmail.com").build();
        Application application = Application.builder()
                .id(appId)
                .candidate(candidate)
                .jobPosition(job)
                .currentStage(screeningStage)
                .status(ApplicationStatus.IN_PROGRESS)
                .build();

        User interviewer1 = User.builder().id(1L).fullName("Interviewer One").email("int1@hirewise.vn").status(UserStatus.ACTIVE).build();
        User interviewer2 = User.builder().id(2L).fullName("Interviewer Two").email("int2@hirewise.vn").status(UserStatus.ACTIVE).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(pipelineStageRepository.findById(20L)).thenReturn(Optional.of(interviewStage));
        when(userRepository.findById(1L)).thenReturn(Optional.of(interviewer1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(interviewer2));
        when(userRepository.getReferenceById(100L)).thenReturn(interviewer1);

        Interview savedInterview = Interview.builder()
                .id(UUID.randomUUID())
                .application(application)
                .scheduledBy(interviewer1)
                .interviewDate(LocalDate.of(2026, 9, 5))
                .interviewTime(LocalTime.of(14, 0))
                .mode(InterviewMode.ONLINE)
                .locationOrLink("https://meet.google.com/xyz")
                .status(InterviewStatus.SCHEDULED)
                .build();
        when(interviewRepository.save(any(Interview.class))).thenReturn(savedInterview);
        when(interviewParticipantRepository.save(any(InterviewParticipant.class))).thenAnswer(i -> i.getArgument(0));

        ScheduleInterviewRequestDto request = ScheduleInterviewRequestDto.builder()
                .targetStageId(20L)
                .interviewerIds(List.of(1L, 2L))
                .interviewDate(LocalDate.of(2026, 9, 5))
                .interviewTime(LocalTime.of(14, 0))
                .mode(InterviewMode.ONLINE)
                .locationOrLink("https://meet.google.com/xyz")
                .notes("Focus on system design")
                .build();

        ScheduleInterviewResponseDto response = interviewService.scheduleInterview(appId, request, recruiterUser);

        assertThat(response).isNotNull();
        assertThat(response.getMode()).isEqualTo(InterviewMode.ONLINE);
        assertThat(response.getToStageId()).isEqualTo(20L);
        assertThat(response.getParticipants()).hasSize(2);

        // Verify stage was moved
        assertThat(application.getCurrentStage()).isEqualTo(interviewStage);
        verify(applicationStageHistoryRepository).save(any(ApplicationStageHistory.class));

        // Verify outbox emails published: 1 for candidate, 2 for interviewers
        verify(outboxEventPublisher, times(1)).publish(eq(OutboxEventType.INTERVIEW_INVITATION_EMAIL), any());
        verify(outboxEventPublisher, times(2)).publish(eq(OutboxEventType.INTERVIEWER_ASSIGNED_EMAIL), any());
    }

    @Test
    @DisplayName("Throws BadRequestException when target stage is not INTERVIEW type")
    void scheduleInterview_targetStageNotInterview_throwsBadRequest() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage screeningStage = PipelineStage.builder()
                .id(10L)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();
        PipelineStage offerStage = PipelineStage.builder()
                .id(30L)
                .stageType(StageType.OFFER)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();

        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).pipelineTemplate(template).build();
        Application application = Application.builder().id(appId).jobPosition(job).currentStage(screeningStage).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(pipelineStageRepository.findById(30L)).thenReturn(Optional.of(offerStage));

        ScheduleInterviewRequestDto request = ScheduleInterviewRequestDto.builder()
                .targetStageId(30L)
                .interviewerIds(List.of(1L))
                .interviewDate(LocalDate.of(2026, 9, 10))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .build();

        assertThatThrownBy(() -> interviewService.scheduleInterview(appId, request, recruiterUser))
                .isInstanceOf(BadRequestException.class);

        verify(interviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws BusinessConflictException when application is already in terminal stage")
    void scheduleInterview_applicationInTerminalStage_throwsConflict() {
        UUID appId = UUID.randomUUID();
        PipelineStage terminalStage = PipelineStage.builder()
                .id(99L)
                .name("Refused")
                .stageType(StageType.TERMINAL_REJECTED)
                .terminal(true)
                .build();
        Application application = Application.builder().id(appId).currentStage(terminalStage).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        ScheduleInterviewRequestDto request = ScheduleInterviewRequestDto.builder()
                .targetStageId(20L)
                .interviewerIds(List.of(1L))
                .interviewDate(LocalDate.of(2026, 9, 10))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .build();

        assertThatThrownBy(() -> interviewService.scheduleInterview(appId, request, recruiterUser))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    @DisplayName("Throws BadRequestException when interview date/time is in the past")
    void scheduleInterview_pastDateTime_throwsBadRequest() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage stage = PipelineStage.builder()
                .id(10L)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();
        PipelineStage interviewStage = PipelineStage.builder()
                .id(20L)
                .stageType(StageType.INTERVIEW)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();

        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).pipelineTemplate(template).build();
        Application application = Application.builder().id(appId).jobPosition(job).currentStage(stage).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(pipelineStageRepository.findById(20L)).thenReturn(Optional.of(interviewStage));

        // Fixed clock is 2026-09-03 10:00 UTC, pass 2026-09-01
        ScheduleInterviewRequestDto request = ScheduleInterviewRequestDto.builder()
                .targetStageId(20L)
                .interviewerIds(List.of(1L))
                .interviewDate(LocalDate.of(2026, 9, 1))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .build();

        assertThatThrownBy(() -> interviewService.scheduleInterview(appId, request, recruiterUser))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Returns list of available active interviewers")
    void getAvailableInterviewers_returnsActiveInterviewers() {
        User u1 = User.builder().id(1L).fullName("Interviewer A").email("a@test.com").build();
        when(userRepository.findActiveUsersByRoleCode(eq("INTERVIEWER"), any(Instant.class))).thenReturn(List.of(u1));

        List<InterviewerOptionDto> options = interviewService.getAvailableInterviewers(recruiterUser);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getFullName()).isEqualTo("Interviewer A");
    }

    @Test
    @DisplayName("Throws BusinessConflictException when interviewer already has conflicting schedule")
    void scheduleInterview_interviewerConflict_throwsBusinessConflict() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage stage = PipelineStage.builder().id(10L).pipelineTemplate(template).terminal(false).active(true).build();
        PipelineStage interviewStage = PipelineStage.builder().id(20L).stageType(StageType.INTERVIEW).pipelineTemplate(template).terminal(false).active(true).build();
        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).pipelineTemplate(template).build();
        Application application = Application.builder().id(appId).jobPosition(job).currentStage(stage).build();

        User interviewer1 = User.builder().id(1L).fullName("Interviewer Conflict").status(UserStatus.ACTIVE).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(pipelineStageRepository.findById(20L)).thenReturn(Optional.of(interviewStage));
        when(userRepository.findById(1L)).thenReturn(Optional.of(interviewer1));
        when(interviewParticipantRepository.existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
                eq(1L), eq(LocalDate.of(2026, 9, 10)), eq(LocalTime.of(10, 0)), eq(InterviewStatus.CANCELLED)
        )).thenReturn(true);

        ScheduleInterviewRequestDto request = ScheduleInterviewRequestDto.builder()
                .targetStageId(20L)
                .interviewerIds(List.of(1L))
                .interviewDate(LocalDate.of(2026, 9, 10))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .build();

        assertThatThrownBy(() -> interviewService.scheduleInterview(appId, request, recruiterUser))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    @DisplayName("Automatically cancels existing scheduled interviews of the application before scheduling new one")
    void scheduleInterview_cancelsExistingScheduledInterviews() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage stage = PipelineStage.builder().id(10L).pipelineTemplate(template).terminal(false).active(true).build();
        PipelineStage interviewStage = PipelineStage.builder().id(20L).stageType(StageType.INTERVIEW).pipelineTemplate(template).terminal(false).active(true).build();
        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).title("Dev").pipelineTemplate(template).build();
        Candidate candidate = Candidate.builder().id(UUID.randomUUID()).fullName("Tran Van B").primaryEmail("tranvanb@gmail.com").build();
        Application application = Application.builder().id(appId).candidate(candidate).jobPosition(job).currentStage(stage).build();

        Interview oldInterview = Interview.builder()
                .id(UUID.randomUUID())
                .status(InterviewStatus.SCHEDULED)
                .build();

        User interviewer1 = User.builder().id(1L).fullName("Interviewer One").status(UserStatus.ACTIVE).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(pipelineStageRepository.findById(20L)).thenReturn(Optional.of(interviewStage));
        when(userRepository.findById(1L)).thenReturn(Optional.of(interviewer1));
        when(userRepository.getReferenceById(100L)).thenReturn(interviewer1);
        when(interviewRepository.findAllByApplication_IdAndStatus(appId, InterviewStatus.SCHEDULED))
                .thenReturn(List.of(oldInterview));

        Interview savedInterview = Interview.builder()
                .id(UUID.randomUUID())
                .application(application)
                .scheduledBy(interviewer1)
                .interviewDate(LocalDate.of(2026, 9, 10))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .status(InterviewStatus.SCHEDULED)
                .build();
        when(interviewRepository.save(any(Interview.class))).thenReturn(savedInterview);
        when(interviewParticipantRepository.save(any(InterviewParticipant.class))).thenAnswer(i -> i.getArgument(0));

        ScheduleInterviewRequestDto request = ScheduleInterviewRequestDto.builder()
                .targetStageId(20L)
                .interviewerIds(List.of(1L))
                .interviewDate(LocalDate.of(2026, 9, 10))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .build();

        interviewService.scheduleInterview(appId, request, recruiterUser);

        assertThat(oldInterview.getStatus()).isEqualTo(InterviewStatus.CANCELLED);
    }

    @Test
    @DisplayName("Interviewer only sees interviews where they are assigned as participant")
    void getScheduleCalendar_interviewerOnlySeesAssignedInterviews() {
        CurrentUser interviewerUser = new CurrentUser(50L, "interviewer@hirewise.vn", "Interviewer User", Set.of("INTERVIEWER"));
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        User interviewer50 = User.builder().id(50L).fullName("Interviewer 50").build();
        User interviewer99 = User.builder().id(99L).fullName("Interviewer 99").build();

        JobPosition job = JobPosition.builder().title("Dev").build();
        Candidate candidate = Candidate.builder().fullName("Candidate A").primaryEmail("a@gmail.com").build();
        Application app = Application.builder().id(UUID.randomUUID()).candidate(candidate).jobPosition(job).build();

        Interview myInterview = Interview.builder()
                .id(UUID.randomUUID())
                .application(app)
                .interviewDate(LocalDate.of(2026, 9, 10))
                .interviewTime(LocalTime.of(10, 0))
                .mode(InterviewMode.ONLINE)
                .status(InterviewStatus.SCHEDULED)
                .build();
        InterviewParticipant myPart = InterviewParticipant.builder().interview(myInterview).interviewer(interviewer50).build();
        myInterview.setParticipants(List.of(myPart));

        Interview otherInterview = Interview.builder()
                .id(UUID.randomUUID())
                .application(app)
                .interviewDate(LocalDate.of(2026, 9, 11))
                .interviewTime(LocalTime.of(14, 0))
                .mode(InterviewMode.ONLINE)
                .status(InterviewStatus.SCHEDULED)
                .build();
        InterviewParticipant otherPart = InterviewParticipant.builder().interview(otherInterview).interviewer(interviewer99).build();
        otherInterview.setParticipants(List.of(otherPart));

        when(interviewRepository.findBetweenDates(start, end)).thenReturn(List.of(myInterview, otherInterview));

        List<com.hirewise.be.dto.response.InterviewCalendarDto> result =
                interviewService.getScheduleCalendar(start, end, interviewerUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInterviewId()).isEqualTo(myInterview.getId());
    }

    @Test
    @DisplayName("HR Admin sees all scheduled interviews across the company")
    void getScheduleCalendar_hrAdminSeesAll() {
        CurrentUser adminUser = new CurrentUser(1L, "admin@hirewise.vn", "Admin", Set.of("HR_ADMIN"));
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        JobPosition job = JobPosition.builder().title("Dev").build();
        Candidate candidate = Candidate.builder().fullName("Candidate A").primaryEmail("a@gmail.com").build();
        Application app = Application.builder().id(UUID.randomUUID()).candidate(candidate).jobPosition(job).build();

        Interview int1 = Interview.builder().id(UUID.randomUUID()).application(app).interviewDate(LocalDate.of(2026, 9, 10)).interviewTime(LocalTime.of(10, 0)).mode(InterviewMode.ONLINE).status(InterviewStatus.SCHEDULED).build();
        Interview int2 = Interview.builder().id(UUID.randomUUID()).application(app).interviewDate(LocalDate.of(2026, 9, 11)).interviewTime(LocalTime.of(14, 0)).mode(InterviewMode.ONLINE).status(InterviewStatus.SCHEDULED).build();

        when(interviewRepository.findBetweenDates(start, end)).thenReturn(List.of(int1, int2));

        List<com.hirewise.be.dto.response.InterviewCalendarDto> result =
                interviewService.getScheduleCalendar(start, end, adminUser);

        assertThat(result).hasSize(2);
    }

    // =========================================================================
    // UC-25: Self-service booking tests
    // =========================================================================

    @Test
    @DisplayName("UC-25: Sends booking link successfully and publishes EM-06 outbox event")
    void sendBookingLink_success() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage stage = PipelineStage.builder()
                .id(10L)
                .name("Screening")
                .stageType(StageType.SCREENING)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();
        Candidate candidate = Candidate.builder()
                .id(UUID.randomUUID())
                .fullName("Nguyen Van B")
                .primaryEmail("candidateb@gmail.com")
                .build();
        JobPosition job = JobPosition.builder()
                .id(UUID.randomUUID())
                .title("Backend Java Engineer")
                .pipelineTemplate(template)
                .build();
        Application app = Application.builder()
                .id(appId)
                .candidate(candidate)
                .jobPosition(job)
                .currentStage(stage)
                .status(ApplicationStatus.IN_PROGRESS)
                .build();

        User interviewer = User.builder()
                .id(20L)
                .fullName("Interviewer B")
                .email("interviewer.b@hirewise.vn")
                .status(UserStatus.ACTIVE)
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(20L)).thenReturn(Optional.of(interviewer));
        when(userRepository.getReferenceById(100L)).thenReturn(User.builder().id(100L).fullName("Recruiter A").build());
        when(interviewBookingRequestRepository.findByApplication_IdOrderByCreatedAtDesc(appId)).thenReturn(List.of());
        when(interviewBookingRequestRepository.save(any(InterviewBookingRequest.class)))
                .thenAnswer(inv -> {
                    InterviewBookingRequest r = inv.getArgument(0);
                    r.setId(500L);
                    return r;
                });

        SendBookingLinkRequestDto request = SendBookingLinkRequestDto.builder()
                .interviewerId(20L)
                .dateRangeStart(LocalDate.of(2026, 9, 10))
                .dateRangeEnd(LocalDate.of(2026, 9, 15))
                .mode(InterviewMode.ONLINE)
                .slots(List.of(
                        SendBookingLinkRequestDto.SlotItemDto.builder()
                                .slotDate(LocalDate.of(2026, 9, 10))
                                .slotTime(LocalTime.of(9, 0))
                                .durationMinutes(45)
                                .build(),
                        SendBookingLinkRequestDto.SlotItemDto.builder()
                                .slotDate(LocalDate.of(2026, 9, 10))
                                .slotTime(LocalTime.of(10, 0))
                                .durationMinutes(45)
                                .build()
                ))
                .build();

        BookingRequestResponseDto response = interviewService.sendBookingLink(appId, request, recruiterUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getBookingLink()).contains(response.getBookingToken().toString());
        assertThat(response.getTotalSlots()).isEqualTo(2);

        verify(interviewBookingSlotRepository, times(2)).save(any(InterviewBookingSlot.class));
        verify(outboxEventPublisher).publish(eq(OutboxEventType.BOOKING_LINK_EMAIL), any());
    }

    @Test
    @DisplayName("UC-25: sendBookingLink with targetStageId transitions stage and records history immediately")
    void sendBookingLink_withTargetStage_transitionsStageImmediately() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage fromStage = PipelineStage.builder()
                .id(10L)
                .name("Screening")
                .stageType(StageType.SCREENING)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();
        PipelineStage targetStage = PipelineStage.builder()
                .id(20L)
                .name("Interview")
                .stageType(StageType.INTERVIEW)
                .pipelineTemplate(template)
                .active(true)
                .terminal(false)
                .build();
        JobPosition job = JobPosition.builder().id(UUID.randomUUID()).title("Backend Engineer").pipelineTemplate(template).build();
        Candidate candidate = Candidate.builder().id(UUID.randomUUID()).fullName("Candidate B").primaryEmail("cand.b@gmail.com").build();
        Application app = Application.builder()
                .id(appId)
                .candidate(candidate)
                .jobPosition(job)
                .currentStage(fromStage)
                .status(ApplicationStatus.IN_PROGRESS)
                .build();
        User interviewer = User.builder()
                .id(20L)
                .fullName("Interviewer B")
                .email("interviewer.b@hirewise.vn")
                .status(UserStatus.ACTIVE)
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(pipelineStageRepository.findById(20L)).thenReturn(Optional.of(targetStage));
        when(userRepository.findById(20L)).thenReturn(Optional.of(interviewer));
        when(userRepository.getReferenceById(100L)).thenReturn(User.builder().id(100L).fullName("Recruiter A").build());
        when(interviewBookingRequestRepository.findByApplication_IdOrderByCreatedAtDesc(appId)).thenReturn(List.of());
        when(interviewBookingRequestRepository.save(any(InterviewBookingRequest.class)))
                .thenAnswer(inv -> {
                    InterviewBookingRequest r = inv.getArgument(0);
                    r.setId(501L);
                    return r;
                });

        SendBookingLinkRequestDto request = SendBookingLinkRequestDto.builder()
                .interviewerId(20L)
                .targetStageId(20L)
                .dateRangeStart(LocalDate.of(2026, 9, 10))
                .dateRangeEnd(LocalDate.of(2026, 9, 15))
                .mode(InterviewMode.ONLINE)
                .slots(List.of(
                        SendBookingLinkRequestDto.SlotItemDto.builder()
                                .slotDate(LocalDate.of(2026, 9, 10))
                                .slotTime(LocalTime.of(9, 0))
                                .durationMinutes(45)
                                .build()
                ))
                .build();

        BookingRequestResponseDto response = interviewService.sendBookingLink(appId, request, recruiterUser);

        assertThat(response).isNotNull();
        assertThat(app.getCurrentStage()).isEqualTo(targetStage);
        verify(applicationRepository).save(app);
        verify(applicationStageHistoryRepository).save(any(ApplicationStageHistory.class));
    }

    @Test
    @DisplayName("UC-25: Fails when interviewer already has a conflict at the specified slot time")
    void sendBookingLink_interviewerConflict_throwsBusinessConflictException() {
        UUID appId = UUID.randomUUID();
        PipelineTemplate template = PipelineTemplate.builder().id(1L).build();
        PipelineStage stage = PipelineStage.builder()
                .id(10L)
                .name("Screening")
                .stageType(StageType.SCREENING)
                .pipelineTemplate(template)
                .terminal(false)
                .active(true)
                .build();
        Candidate candidate = Candidate.builder().fullName("Candidate").primaryEmail("c@gmail.com").build();
        JobPosition job = JobPosition.builder().title("Dev").pipelineTemplate(template).build();
        Application app = Application.builder().id(appId).candidate(candidate).jobPosition(job).currentStage(stage).build();

        User interviewer = User.builder().id(20L).fullName("Interviewer").status(UserStatus.ACTIVE).build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(userRepository.findById(20L)).thenReturn(Optional.of(interviewer));
        when(userRepository.getReferenceById(100L)).thenReturn(User.builder().id(100L).build());
        when(interviewBookingRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SendBookingLinkRequestDto request = SendBookingLinkRequestDto.builder()
                .interviewerId(20L)
                .dateRangeStart(LocalDate.of(2026, 9, 10))
                .dateRangeEnd(LocalDate.of(2026, 9, 15))
                .mode(InterviewMode.ONLINE)
                .slots(List.of(
                        SendBookingLinkRequestDto.SlotItemDto.builder()
                                .slotDate(LocalDate.of(2026, 9, 10))
                                .slotTime(LocalTime.of(9, 0))
                                .build()
                ))
                .build();

        BookingRequestResponseDto resp = interviewService.sendBookingLink(appId, request, recruiterUser);

        assertThat(resp).isNotNull();
        assertThat(resp.getTotalSlots()).isEqualTo(1);
        verify(interviewBookingSlotRepository).save(any());
    }

    @Test
    @DisplayName("UC-25: getInterviewerBusySlots returns busy slots successfully")
    void getInterviewerBusySlots_returnsSlots() {
        LocalDate start = LocalDate.of(2026, 9, 10);
        LocalDate end = LocalDate.of(2026, 9, 14);
        com.hirewise.be.dto.response.InterviewerBusySlotDto slot = com.hirewise.be.dto.response.InterviewerBusySlotDto.builder()
                .date(start)
                .time(LocalTime.of(9, 0))
                .build();

        when(interviewParticipantRepository.findBusySlotsByInterviewer(eq(20L), eq(start), eq(end), eq(InterviewStatus.CANCELLED)))
                .thenReturn(List.of(slot));

        List<com.hirewise.be.dto.response.InterviewerBusySlotDto> result =
                interviewService.getInterviewerBusySlots(20L, start, end, recruiterUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(start);
        assertThat(result.get(0).getTime()).isEqualTo(LocalTime.of(9, 0));
    }

    // =========================================================================
    // UC-34: Candidate views booking page
    // =========================================================================

    @Test
    @DisplayName("UC-34: Candidate loads booking page successfully")
    void getBookingPage_success() {
        UUID token = UUID.randomUUID();
        Candidate candidate = Candidate.builder().fullName("Tran Thi C").build();
        JobPosition job = JobPosition.builder().title("QA Engineer").build();
        Application app = Application.builder().candidate(candidate).jobPosition(job).build();
        User interviewer = User.builder().fullName("Interviewer C").build();

        InterviewBookingRequest req = InterviewBookingRequest.builder()
                .id(1L)
                .bookingToken(token)
                .application(app)
                .interviewer(interviewer)
                .status(InterviewBookingRequestStatus.OPEN)
                .expiresAt(fixedInstant.plusSeconds(86400 * 5))
                .mode(InterviewMode.ONLINE)
                .build();

        InterviewBookingSlot slot1 = InterviewBookingSlot.builder()
                .id(101L)
                .slotDate(LocalDate.of(2026, 9, 10))
                .slotTime(LocalTime.of(9, 0))
                .durationMinutes(45)
                .status(InterviewBookingSlotStatus.OPEN)
                .build();

        when(interviewBookingRequestRepository.findByBookingTokenFetch(token)).thenReturn(Optional.of(req));
        when(interviewBookingSlotRepository.findByBookingRequestIdOrderBySlotDateAscSlotTimeAsc(1L))
                .thenReturn(List.of(slot1));

        BookingPageResponseDto page = interviewService.getBookingPage(token);

        assertThat(page.getCandidateName()).isEqualTo("Tran Thi C");
        assertThat(page.getJobTitle()).isEqualTo("QA Engineer");
        assertThat(page.getInterviewerName()).isEqualTo("Interviewer C");
        assertThat(page.getSlots()).hasSize(1);
        assertThat(page.getSlots().get(0).getId()).isEqualTo(101L);
        assertThat(page.getSlots().get(0).isAvailable()).isTrue();
    }

    @Test
    @DisplayName("UC-34: Candidate opens booking page and slots that are conflicting are marked as BUSY and unavailable")
    void getBookingPage_marksConflictingSlotsAsBusy_whenInterviewerBooked() {
        UUID token = UUID.randomUUID();
        Candidate candidate = Candidate.builder().fullName("Tran Thi C").build();
        JobPosition job = JobPosition.builder().title("QA Engineer").build();
        Application app = Application.builder().candidate(candidate).jobPosition(job).build();
        User interviewer = User.builder().id(20L).fullName("Interviewer C").build();

        InterviewBookingRequest req = InterviewBookingRequest.builder()
                .id(1L)
                .bookingToken(token)
                .application(app)
                .interviewer(interviewer)
                .status(InterviewBookingRequestStatus.OPEN)
                .expiresAt(fixedInstant.plusSeconds(86400 * 5))
                .mode(InterviewMode.ONLINE)
                .build();

        InterviewBookingSlot slot1 = InterviewBookingSlot.builder()
                .id(101L)
                .slotDate(LocalDate.of(2026, 9, 10))
                .slotTime(LocalTime.of(9, 0))
                .durationMinutes(45)
                .status(InterviewBookingSlotStatus.OPEN)
                .build();

        InterviewBookingSlot slot2 = InterviewBookingSlot.builder()
                .id(102L)
                .slotDate(LocalDate.of(2026, 9, 10))
                .slotTime(LocalTime.of(10, 0))
                .durationMinutes(45)
                .status(InterviewBookingSlotStatus.OPEN)
                .build();

        when(interviewBookingRequestRepository.findByBookingTokenFetch(token)).thenReturn(Optional.of(req));
        when(interviewBookingSlotRepository.findByBookingRequestIdOrderBySlotDateAscSlotTimeAsc(1L))
                .thenReturn(List.of(slot1, slot2));

        // slot1 is conflicting (e.g. interviewer booked another meeting)
        when(interviewParticipantRepository.existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
                eq(20L), eq(LocalDate.of(2026, 9, 10)), eq(LocalTime.of(9, 0)), eq(InterviewStatus.CANCELLED)
        )).thenReturn(true);

        // slot2 is free
        when(interviewParticipantRepository.existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
                eq(20L), eq(LocalDate.of(2026, 9, 10)), eq(LocalTime.of(10, 0)), eq(InterviewStatus.CANCELLED)
        )).thenReturn(false);

        BookingPageResponseDto page = interviewService.getBookingPage(token);

        // Both slots returned, slot1 is BUSY (unavailable), slot2 is OPEN (available)
        assertThat(page.getSlots()).hasSize(2);
        BookingPageResponseDto.BookingSlotDto s1 = page.getSlots().stream().filter(s -> s.getId().equals(101L)).findFirst().orElseThrow();
        BookingPageResponseDto.BookingSlotDto s2 = page.getSlots().stream().filter(s -> s.getId().equals(102L)).findFirst().orElseThrow();

        assertThat(s1.getStatus()).isEqualTo(InterviewBookingSlotStatus.BUSY);
        assertThat(s1.isAvailable()).isFalse();
        assertThat(s1.getUnavailableReason()).contains("Người phỏng vấn đã có lịch bận");

        assertThat(s2.getStatus()).isEqualTo(InterviewBookingSlotStatus.OPEN);
        assertThat(s2.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("UC-34: Fails when booking token has expired")
    void getBookingPage_expired_throwsBusinessConflictException() {
        UUID token = UUID.randomUUID();
        InterviewBookingRequest req = InterviewBookingRequest.builder()
                .id(1L)
                .bookingToken(token)
                .status(InterviewBookingRequestStatus.OPEN)
                .expiresAt(fixedInstant.minusSeconds(3600)) // expired 1 hour ago
                .build();

        when(interviewBookingRequestRepository.findByBookingTokenFetch(token)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> interviewService.getBookingPage(token))
                .isInstanceOf(BusinessConflictException.class);
    }

}
