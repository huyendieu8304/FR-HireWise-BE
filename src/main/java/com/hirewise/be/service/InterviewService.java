package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewParticipant;
import com.hirewise.be.domain.InterviewStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.dto.request.ScheduleInterviewRequestDto;
import com.hirewise.be.dto.response.InterviewerOptionDto;
import com.hirewise.be.dto.response.ScheduleInterviewResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.InterviewMapper;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.InterviewParticipantRepository;
import com.hirewise.be.repository.InterviewRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.domain.InterviewBookingRequest;
import com.hirewise.be.domain.InterviewBookingRequestStatus;
import com.hirewise.be.domain.InterviewBookingSlot;
import com.hirewise.be.domain.InterviewBookingSlotStatus;
import com.hirewise.be.dto.request.ConfirmBookingSlotRequestDto;
import com.hirewise.be.dto.request.SendBookingLinkRequestDto;
import com.hirewise.be.dto.response.BookingConfirmResponseDto;
import com.hirewise.be.dto.response.BookingPageResponseDto;
import com.hirewise.be.dto.response.BookingRequestResponseDto;
import com.hirewise.be.repository.InterviewBookingRequestRepository;
import com.hirewise.be.repository.InterviewBookingSlotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for UC-24: Schedule predetermined interview (fixed schedule)
 * and UC-25/UC-34/UC-35: Self-service interview booking.
 */
@Slf4j
@Service
public class InterviewService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final InterviewRepository interviewRepository;
    private final InterviewParticipantRepository interviewParticipantRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStageHistoryRepository applicationStageHistoryRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final UserRepository userRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AccessControlService accessControlService;
    private final CalendarIntegrationService calendarIntegrationService;
    private final Clock clock;
    private final InterviewBookingRequestRepository interviewBookingRequestRepository;
    private final InterviewBookingSlotRepository interviewBookingSlotRepository;
    private final String bookingLinkBaseUrl;
    /** RBAC layer 3 — used by getScheduleCalendar to resolve Hiring Manager department scopes. */
    private final UserAccessScopeRepository userAccessScopeRepository;
    private final DepartmentRepository departmentRepository;

    public InterviewService(
            InterviewRepository interviewRepository,
            InterviewParticipantRepository interviewParticipantRepository,
            ApplicationRepository applicationRepository,
            ApplicationStageHistoryRepository applicationStageHistoryRepository,
            PipelineStageRepository pipelineStageRepository,
            UserRepository userRepository,
            OutboxEventPublisher outboxEventPublisher,
            AccessControlService accessControlService,
            CalendarIntegrationService calendarIntegrationService,
            Clock clock,
            InterviewBookingRequestRepository interviewBookingRequestRepository,
            InterviewBookingSlotRepository interviewBookingSlotRepository,
            @Value("${app.booking.link-base-url:http://localhost:5173/booking}") String bookingLinkBaseUrl,
            UserAccessScopeRepository userAccessScopeRepository,
            DepartmentRepository departmentRepository) {
        this.interviewRepository = interviewRepository;
        this.interviewParticipantRepository = interviewParticipantRepository;
        this.applicationRepository = applicationRepository;
        this.applicationStageHistoryRepository = applicationStageHistoryRepository;
        this.pipelineStageRepository = pipelineStageRepository;
        this.userRepository = userRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.accessControlService = accessControlService;
        this.calendarIntegrationService = calendarIntegrationService;
        this.clock = clock;
        this.interviewBookingRequestRepository = interviewBookingRequestRepository;
        this.interviewBookingSlotRepository = interviewBookingSlotRepository;
        this.bookingLinkBaseUrl = bookingLinkBaseUrl;
        this.userAccessScopeRepository = userAccessScopeRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * UC-24 main flow: Schedules an interview for an application, assigns interviewers,
     * transitions the application to the target INTERVIEW stage, and enqueues invitation emails.
     *
     * @param applicationId the application being scheduled
     * @param request       the interview schedule parameters
     * @param currentUser   the authenticated Recruiter
     * @return details of the scheduled interview and the stage move outcome
     */
    @Transactional
    public ScheduleInterviewResponseDto scheduleInterview(
            UUID applicationId, ScheduleInterviewRequestDto request, CurrentUser currentUser) {

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        PipelineStage fromStage = application.getCurrentStage();
        if (fromStage.isTerminal()) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_STAGE_TERMINAL, fromStage.getName());
        }

        PipelineStage toStage = pipelineStageRepository.findById(request.getTargetStageId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PIPELINE_STAGE_NOT_FOUND, request.getTargetStageId()));

        Long pipelineTemplateId = application.getJobPosition().getPipelineTemplate().getId();
        if (!toStage.getPipelineTemplate().getId().equals(pipelineTemplateId)) {
            throw new BadRequestException(ErrorCode.INVALID_STAGE_TRANSITION);
        }
        if (!toStage.isActive()) {
            throw new BusinessConflictException(ErrorCode.PIPELINE_STAGE_INACTIVE, toStage.getId());
        }
        if (toStage.getStageType() != StageType.INTERVIEW) {
            throw new BadRequestException(ErrorCode.INTERVIEW_STAGE_NOT_INTERVIEW_TYPE);
        }

        // Validate interview datetime is not in the past
        LocalDateTime interviewDateTime = LocalDateTime.of(request.getInterviewDate(), request.getInterviewTime());
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(clock), clock.getZone());
        if (interviewDateTime.isBefore(currentDateTime)) {
            throw new BadRequestException(ErrorCode.INTERVIEW_TIME_IN_PAST);
        }

        Instant now = Instant.now(clock);

        // Auto-cancel previous SCHEDULED interview(s) for this application before scheduling a new one
        List<Interview> existingScheduledInterviews = interviewRepository.findAllByApplication_IdAndStatus(
                applicationId, InterviewStatus.SCHEDULED);
        for (Interview oldInterview : existingScheduledInterviews) {
            oldInterview.setStatus(InterviewStatus.CANCELLED);
            oldInterview.setUpdatedAt(now);
            interviewRepository.save(oldInterview);
        }
        if (!existingScheduledInterviews.isEmpty()) {
            interviewRepository.flush();
            log.info("Cancelled {} previous scheduled interview(s) for application {}",
                    existingScheduledInterviews.size(), applicationId);
        }

        // Validate and fetch interviewers, checking for schedule conflict
        List<User> interviewers = new ArrayList<>();
        for (Long interviewerId : request.getInterviewerIds()) {
            User interviewer = userRepository.findById(interviewerId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTERVIEW_INTERVIEWER_NOT_FOUND, interviewerId));
            if (interviewer.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessConflictException(ErrorCode.INTERVIEW_INTERVIEWER_INACTIVE, interviewer.getFullName());
            }
            boolean hasConflict = interviewParticipantRepository
                    .existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
                            interviewerId, request.getInterviewDate(), request.getInterviewTime(), InterviewStatus.CANCELLED);
            if (hasConflict) {
                throw new BusinessConflictException(ErrorCode.INTERVIEWER_TIME_CONFLICT, interviewer.getFullName());
            }
            interviewers.add(interviewer);
        }

        // 1. Move stage (audit trail)
        application.setCurrentStage(toStage);
        application.setStatus(ApplicationStatus.IN_PROGRESS);
        application.setLastStageChangedAt(now);
        application.setSlaAlertSentAt(null); // UC-41: fresh stage-dwell, un-alerted
        application.setUpdatedAt(now);
        applicationRepository.save(application);

        ApplicationStageHistory history = ApplicationStageHistory.builder()
                .application(application)
                .fromStage(fromStage)
                .toStage(toStage)
                .changedBy(userRepository.getReferenceById(currentUser.userId()))
                .transitionType(StageTransitionType.MANUAL)
                .changedAt(now)
                .build();
        applicationStageHistoryRepository.save(history);

        // 2. Persist interview (Auto-create real Google Meet link via Google Calendar if mode is ONLINE and link is empty)
        String effectiveLocationOrLink = request.getLocationOrLink();
        if (request.getMode() == InterviewMode.ONLINE) {
            if (effectiveLocationOrLink == null || effectiveLocationOrLink.isBlank()) {
                String summary = String.format("Phỏng vấn %s - %s",
                        application.getCandidate().getFullName(),
                        application.getJobPosition().getTitle());
                String description = String.format("Phỏng vấn tuyển dụng vị trí %s cho ứng viên %s",
                        application.getJobPosition().getTitle(),
                        application.getCandidate().getFullName());
                LocalDateTime start = LocalDateTime.of(request.getInterviewDate(), request.getInterviewTime());
                LocalDateTime end = start.plusMinutes(45);

                List<String> attendeeEmails = new ArrayList<>();
                for (User interviewer : interviewers) {
                    if (interviewer.getEmail() != null && !interviewer.getEmail().isBlank()) {
                        attendeeEmails.add(interviewer.getEmail());
                    }
                }
                if (application.getCandidate().getPrimaryEmail() != null && !application.getCandidate().getPrimaryEmail().isBlank()) {
                    attendeeEmails.add(application.getCandidate().getPrimaryEmail());
                }

                effectiveLocationOrLink = calendarIntegrationService.createGoogleMeetMeeting(
                                summary, description, start, end, attendeeEmails)
                        .orElseGet(InterviewService::generateGoogleMeetLink);
            }
        }

        User scheduledByUser = userRepository.getReferenceById(currentUser.userId());
        Interview interview = Interview.builder()
                .application(application)
                .scheduledBy(scheduledByUser)
                // UC-28: anchors this interview permanently to the (Job, Stage) Scorecard
                // that was configured for toStage - see Interview#pipelineStage Javadoc.
                .pipelineStage(toStage)
                .interviewDate(request.getInterviewDate())
                .interviewTime(request.getInterviewTime())
                .mode(request.getMode())
                .locationOrLink(effectiveLocationOrLink)
                .status(InterviewStatus.SCHEDULED)
                .notes(request.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();
        interview = interviewRepository.save(interview);


        // 3. Persist participants
        List<InterviewParticipant> participants = new ArrayList<>();
        for (User interviewer : interviewers) {
            InterviewParticipant participant = InterviewParticipant.builder()
                    .interview(interview)
                    .interviewer(interviewer)
                    .createdAt(now)
                    .build();
            participants.add(interviewParticipantRepository.save(participant));
        }

        // 4. Enqueue email EM-05 for candidate
        String candidateEmail = application.getCandidate().getPrimaryEmail();
        String candidateName = application.getCandidate().getFullName();
        String jobTitle = application.getJobPosition().getTitle();
        String formattedDate = request.getInterviewDate().format(DATE_FORMATTER);
        String formattedTime = request.getInterviewTime().format(TIME_FORMATTER);
        String modeDisplay = request.getMode().name();
        String location = effectiveLocationOrLink != null ? effectiveLocationOrLink : "";
        String recruiterName = currentUser.fullName();

        outboxEventPublisher.publish(
                OutboxEventType.INTERVIEW_INVITATION_EMAIL,
                OutboxPayloads.interviewInvitationEmail(
                        candidateEmail,
                        candidateName,
                        jobTitle,
                        formattedDate,
                        formattedTime,
                        modeDisplay,
                        location,
                        recruiterName
                )
        );

        // 5. Enqueue email EM-08 for each assigned interviewer
        for (User interviewer : interviewers) {
            outboxEventPublisher.publish(
                    OutboxEventType.INTERVIEWER_ASSIGNED_EMAIL,
                    OutboxPayloads.interviewerAssignedEmail(
                            interviewer.getEmail(),
                            interviewer.getFullName(),
                            candidateName,
                            jobTitle,
                            formattedDate,
                            formattedTime,
                            location
                    )
            );
        }

        log.info("Interview {} scheduled for application {} by user {} (stage {} -> {})",
                interview.getId(), applicationId, currentUser.userId(), fromStage.getId(), toStage.getId());

        return InterviewMapper.toScheduleResponseDto(interview, participants, application, fromStage.getId());
    }

    /**
     * Retrieves active users holding the INTERVIEWER role for selection in the UI.
     *
     * @param currentUser authenticated caller
     * @return list of available interviewers
     */
    public List<InterviewerOptionDto> getAvailableInterviewers(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW, ResourceContext.none());
        List<User> activeInterviewers = userRepository.findActiveUsersByRoleCode("INTERVIEWER", Instant.now(clock));
        return activeInterviewers.stream()
                .map(InterviewMapper::toInterviewerOptionDto)
                .toList();
    }

    /**
     * Retrieves busy time slots for a specific interviewer between dates to avoid scheduling conflicts.
     */
    public List<com.hirewise.be.dto.response.InterviewerBusySlotDto> getInterviewerBusySlots(
            Long interviewerId, LocalDate startDate, LocalDate endDate, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW, ResourceContext.none());
        return interviewParticipantRepository.findBusySlotsByInterviewer(
                interviewerId, startDate, endDate, InterviewStatus.CANCELLED);
    }

    /**
     * Retrieves scheduled interviews within a date range for the calendar visual grid (UC-24).
     *
     * <p>Visibility rules (RBAC):
     * <ul>
     *   <li><b>HR_ADMIN</b> — sees every interview company-wide.</li>
     *   <li><b>HIRING_MANAGER</b> — sees interviews belonging to any job whose department
     *       falls within the HM's active Access Scopes (DEPARTMENT or SYSTEM).</li>
     *   <li><b>RECRUITER</b> — sees interviews belonging to jobs they own
     *       ({@code recruiter_id} or {@code created_by_user_id}).</li>
     *   <li><b>INTERVIEWER</b> (default) — sees only interviews they are assigned to
     *       as a participant.</li>
     * </ul>
     */
    public List<com.hirewise.be.dto.response.InterviewCalendarDto> getScheduleCalendar(
            LocalDate startDate, LocalDate endDate, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW, ResourceContext.none());
        List<Interview> interviews = interviewRepository.findBetweenDates(startDate, endDate);

        if (!currentUser.hasRole("HR_ADMIN")) {
            Long currentUserId = currentUser.userId();

            if (currentUser.hasRole("HIRING_MANAGER")) {
                // ── Hiring Manager: filter by RBAC-layer-3 department scopes ──────────────
                // Resolve all department IDs the HM is allowed to see (including sub-departments).
                Set<Long> allowedDeptIds = resolveHiringManagerDepartmentIds(currentUserId);
                if (allowedDeptIds == null) {
                    // SYSTEM scope → HM can see everything (equivalent to HR_ADMIN for calendar)
                    // no-op: do not filter
                } else {
                    interviews = interviews.stream().filter(i -> {
                        if (i.getApplication() == null || i.getApplication().getJobPosition() == null) return false;
                        var dept = i.getApplication().getJobPosition().getDepartment();
                        return dept != null && allowedDeptIds.contains(dept.getId());
                    }).toList();
                }

            } else if (currentUser.hasRole("RECRUITER")) {
                // ── Recruiter: filter by job ownership ────────────────────────────────────
                interviews = interviews.stream().filter(i -> {
                    if (i.getApplication() == null || i.getApplication().getJobPosition() == null) return false;
                    var job = i.getApplication().getJobPosition();
                    boolean isJobRecruiter = job.getRecruiter() != null
                            && currentUserId.equals(job.getRecruiter().getId());
                    boolean isJobCreator = currentUserId.equals(job.getCreatedByUserId());
                    boolean isScheduledBy = i.getScheduledBy() != null
                            && currentUserId.equals(i.getScheduledBy().getId());
                    return isJobRecruiter || isJobCreator || isScheduledBy;
                }).toList();

            } else {
                // ── Interviewer (and any other role): only assigned interviews ─────────────
                interviews = interviews.stream().filter(i ->
                    i.getParticipants() != null && i.getParticipants().stream()
                            .anyMatch(p -> p.getInterviewer() != null
                                    && currentUserId.equals(p.getInterviewer().getId()))
                ).toList();
            }
        }

        return interviews.stream().map(i -> {
            List<String> participantNames = i.getParticipants() != null
                    ? i.getParticipants().stream()
                        .map(p -> p.getInterviewer().getFullName())
                        .toList()
                    : List.of();
            return com.hirewise.be.dto.response.InterviewCalendarDto.builder()
                    .interviewId(i.getId())
                    .applicationId(i.getApplication().getId())
                    .candidateName(i.getApplication().getCandidate().getFullName())
                    .candidateEmail(i.getApplication().getCandidate().getPrimaryEmail())
                    .jobTitle(i.getApplication().getJobPosition().getTitle())
                    .interviewDate(i.getInterviewDate())
                    .interviewTime(i.getInterviewTime())
                    .mode(i.getMode())
                    .locationOrLink(i.getLocationOrLink())
                    .status(i.getStatus())
                    .interviewerNames(participantNames)
                    .notes(i.getNotes())
                    .build();
        }).toList();
    }

    /**
     * Resolves the set of department IDs visible to a Hiring Manager based on their
     * active Access Scopes (RBAC layer 3).
     *
     * @param userId the Hiring Manager's user ID
     * @return set of allowed department IDs (including sub-departments),
     *         or {@code null} if the HM has a SYSTEM-level scope (sees everything)
     */
    private Set<Long> resolveHiringManagerDepartmentIds(Long userId) {
        List<UserAccessScope> scopes = userAccessScopeRepository.findActiveScopes(userId, Instant.now(clock));
        Set<Long> allowedDeptIds = new HashSet<>();
        for (UserAccessScope scope : scopes) {
            if (scope.getScopeType() == ScopeType.SYSTEM) {
                return null; // SYSTEM scope → no restriction needed
            }
            if (scope.getScopeType() == ScopeType.DEPARTMENT && scope.getDepartment() != null) {
                Long rootId = scope.getDepartment().getId();
                if (scope.isIncludeSubDepartments()) {
                    allowedDeptIds.addAll(departmentRepository.findSelfAndDescendantIds(rootId));
                } else {
                    allowedDeptIds.add(rootId);
                }
            }
        }
        return allowedDeptIds;
    }

    /**
     * Generates a Google Meet-style URL as fallback when Google Calendar is not connected.
     * Mimics the standard Google Meet room code format: xxx-yyyy-zzz (3-4-3 lowercase letters).
     * Format: https://meet.google.com/xxx-yyyy-zzz
     */
    public static String generateGoogleMeetLink() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder("https://meet.google.com/");
        // First segment: 3 characters
        for (int i = 0; i < 3; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        sb.append('-');
        // Second segment: 4 characters
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        sb.append('-');
        // Third segment: 3 characters
        for (int i = 0; i < 3; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * UC-25: Sends a self-service booking link to a candidate.
     */
    @Transactional
    public BookingRequestResponseDto sendBookingLink(
            UUID applicationId, SendBookingLinkRequestDto request, CurrentUser currentUser) {

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        PipelineStage fromStage = application.getCurrentStage();
        if (fromStage.isTerminal()) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_STAGE_TERMINAL, fromStage.getName());
        }

        PipelineStage targetStage = null;
        if (request.getTargetStageId() != null) {
            targetStage = pipelineStageRepository.findById(request.getTargetStageId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.PIPELINE_STAGE_NOT_FOUND, request.getTargetStageId()));
            Long pipelineTemplateId = application.getJobPosition().getPipelineTemplate().getId();
            if (!targetStage.getPipelineTemplate().getId().equals(pipelineTemplateId)) {
                throw new BadRequestException(ErrorCode.INVALID_STAGE_TRANSITION);
            }
            if (!targetStage.isActive()) {
                throw new BusinessConflictException(ErrorCode.PIPELINE_STAGE_INACTIVE, targetStage.getId());
            }
            if (targetStage.getStageType() != StageType.INTERVIEW) {
                throw new BadRequestException(ErrorCode.INTERVIEW_STAGE_NOT_INTERVIEW_TYPE);
            }
        }

        User interviewer = userRepository.findById(request.getInterviewerId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTERVIEW_INTERVIEWER_NOT_FOUND, request.getInterviewerId()));
        if (interviewer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessConflictException(ErrorCode.INTERVIEW_INTERVIEWER_INACTIVE, interviewer.getFullName());
        }

        LocalDate today = LocalDate.now(clock);
        if (request.getDateRangeStart().isBefore(today)) {
            throw new BadRequestException(ErrorCode.INTERVIEW_TIME_IN_PAST);
        }
        if (request.getDateRangeEnd().isBefore(request.getDateRangeStart())) {
            throw new BadRequestException(ErrorCode.INTERVIEW_TIME_IN_PAST);
        }

        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(clock), clock.getZone());
        for (SendBookingLinkRequestDto.SlotItemDto slot : request.getSlots()) {
            LocalDateTime slotDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getSlotTime());
            if (slotDateTime.isBefore(currentDateTime)) {
                throw new BadRequestException(ErrorCode.INTERVIEW_TIME_IN_PAST);
            }
            if (slot.getSlotDate().isBefore(request.getDateRangeStart()) || slot.getSlotDate().isAfter(request.getDateRangeEnd())) {
                throw new BadRequestException(ErrorCode.INTERVIEW_TIME_IN_PAST);
            }
        }

        Instant now = Instant.now(clock);
        // Expiry 7 days from now
        Instant expiresAt = now.plus(7, java.time.temporal.ChronoUnit.DAYS);
        UUID bookingToken = UUID.randomUUID();

        // Cancel existing OPEN booking requests for this application
        List<InterviewBookingRequest> existingRequests = interviewBookingRequestRepository
                .findByApplication_IdOrderByCreatedAtDesc(applicationId);
        for (InterviewBookingRequest req : existingRequests) {
            if (req.getStatus() == InterviewBookingRequestStatus.OPEN) {
                req.setStatus(InterviewBookingRequestStatus.CANCELLED);
                interviewBookingRequestRepository.save(req);
            }
        }

        User createdByUser = userRepository.getReferenceById(currentUser.userId());

        // Transition application stage if targetStage is specified and differs from current stage
        if (targetStage != null && !targetStage.getId().equals(fromStage.getId())) {
            application.setCurrentStage(targetStage);
            application.setStatus(ApplicationStatus.IN_PROGRESS);
            application.setLastStageChangedAt(now);
            application.setSlaAlertSentAt(null); // UC-41: fresh stage-dwell, un-alerted
            application.setUpdatedAt(now);
            applicationRepository.save(application);

            ApplicationStageHistory history = ApplicationStageHistory.builder()
                    .application(application)
                    .fromStage(fromStage)
                    .toStage(targetStage)
                    .changedBy(createdByUser)
                    .transitionType(StageTransitionType.MANUAL)
                    .changedAt(now)
                    .build();
            applicationStageHistoryRepository.save(history);
        }

        InterviewBookingRequest bookingRequest = InterviewBookingRequest.builder()
                .application(application)
                .interviewer(interviewer)
                .dateRangeStart(request.getDateRangeStart())
                .dateRangeEnd(request.getDateRangeEnd())
                .bookingToken(bookingToken)
                .expiresAt(expiresAt)
                .status(InterviewBookingRequestStatus.OPEN)
                .targetStage(targetStage)
                .mode(request.getMode())
                .locationOrLink(request.getLocationOrLink())
                .createdBy(createdByUser)
                .createdAt(now)
                .build();
        bookingRequest = interviewBookingRequestRepository.save(bookingRequest);

        for (SendBookingLinkRequestDto.SlotItemDto s : request.getSlots()) {
            InterviewBookingSlot slot = InterviewBookingSlot.builder()
                    .bookingRequest(bookingRequest)
                    .slotDate(s.getSlotDate())
                    .slotTime(s.getSlotTime())
                    .durationMinutes(s.getDurationMinutes() != null ? s.getDurationMinutes() : 45)
                    .status(InterviewBookingSlotStatus.OPEN)
                    .build();
            interviewBookingSlotRepository.save(slot);
        }

        String fullBookingLink = String.format("%s/%s", bookingLinkBaseUrl.replaceAll("/$", ""), bookingToken);

        // Enqueue email EM-06 for candidate
        String candidateEmail = application.getCandidate().getPrimaryEmail();
        String candidateName = application.getCandidate().getFullName();
        String jobTitle = application.getJobPosition().getTitle();
        String recruiterName = currentUser.fullName();

        outboxEventPublisher.publish(
                OutboxEventType.BOOKING_LINK_EMAIL,
                OutboxPayloads.bookingLinkEmail(
                        candidateEmail,
                        candidateName,
                        jobTitle,
                        fullBookingLink,
                        "168",
                        recruiterName
                )
        );

        log.info("Booking link {} sent for application {} by recruiter {}",
                bookingToken, applicationId, currentUser.userId());

        return BookingRequestResponseDto.builder()
                .id(bookingRequest.getId())
                .bookingToken(bookingToken)
                .bookingLink(fullBookingLink)
                .dateRangeStart(bookingRequest.getDateRangeStart())
                .dateRangeEnd(bookingRequest.getDateRangeEnd())
                .expiresAt(expiresAt)
                .status(InterviewBookingRequestStatus.OPEN)
                .interviewerId(interviewer.getId())
                .interviewerName(interviewer.getFullName())
                .totalSlots(request.getSlots().size())
                .build();
    }

    /**
     * UC-34: Candidate views the public booking page for a token.
     */
    @Transactional(readOnly = true)
    public BookingPageResponseDto getBookingPage(UUID token) {
        InterviewBookingRequest bookingRequest = interviewBookingRequestRepository
                .findByBookingTokenFetch(token)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_TOKEN_INVALID, token));

        Instant now = Instant.now(clock);
        if (bookingRequest.getStatus() != InterviewBookingRequestStatus.OPEN || bookingRequest.getExpiresAt().isBefore(now)) {
            throw new BusinessConflictException(ErrorCode.BOOKING_TOKEN_EXPIRED);
        }

        List<InterviewBookingSlot> slots = interviewBookingSlotRepository
                .findByBookingRequestIdOrderBySlotDateAscSlotTimeAsc(bookingRequest.getId());

        User interviewer = bookingRequest.getInterviewer();

        List<BookingPageResponseDto.BookingSlotDto> slotDtos = slots.stream()
                .map(s -> {
                    boolean isOpenInDb = s.getStatus() == InterviewBookingSlotStatus.OPEN
                            || (s.getStatus() == InterviewBookingSlotStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isBefore(now));

                    boolean hasConflict = false;
                    if (interviewer != null && interviewer.getId() != null) {
                        hasConflict = interviewParticipantRepository
                                .existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
                                        interviewer.getId(), s.getSlotDate(), s.getSlotTime(), InterviewStatus.CANCELLED);
                    }

                    boolean available = isOpenInDb && !hasConflict;
                    InterviewBookingSlotStatus effectiveStatus;
                    String reason = null;

                    if (!isOpenInDb) {
                        effectiveStatus = s.getStatus() != null ? s.getStatus() : InterviewBookingSlotStatus.BOOKED;
                        reason = "Khung giờ đã được đặt";
                    } else if (hasConflict) {
                        effectiveStatus = InterviewBookingSlotStatus.BUSY;
                        reason = "Người phỏng vấn đã có lịch bận";
                    } else {
                        effectiveStatus = InterviewBookingSlotStatus.OPEN;
                    }

                    return BookingPageResponseDto.BookingSlotDto.builder()
                            .id(s.getId())
                            .slotDate(s.getSlotDate())
                            .slotTime(s.getSlotTime())
                            .durationMinutes(s.getDurationMinutes())
                            .status(effectiveStatus)
                            .available(available)
                            .unavailableReason(reason)
                            .build();
                })
                .toList();

        return BookingPageResponseDto.builder()
                .bookingToken(bookingRequest.getBookingToken())
                .candidateName(bookingRequest.getApplication().getCandidate().getFullName())
                .jobTitle(bookingRequest.getApplication().getJobPosition().getTitle())
                .interviewerName(bookingRequest.getInterviewer().getFullName())
                .mode(bookingRequest.getMode())
                .locationOrLink(bookingRequest.getLocationOrLink())
                .dateRangeStart(bookingRequest.getDateRangeStart())
                .dateRangeEnd(bookingRequest.getDateRangeEnd())
                .expiresAt(bookingRequest.getExpiresAt())
                .status(bookingRequest.getStatus())
                .slots(slotDtos)
                .build();
    }

    /**
     * UC-35: Candidate confirms a selected booking slot.
     */
    @Transactional
    public BookingConfirmResponseDto confirmBookingSlot(UUID token, ConfirmBookingSlotRequestDto request) {
        InterviewBookingRequest bookingRequest = interviewBookingRequestRepository
                .findByBookingTokenFetch(token)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_TOKEN_INVALID, token));

        Instant now = Instant.now(clock);
        if (bookingRequest.getStatus() != InterviewBookingRequestStatus.OPEN || bookingRequest.getExpiresAt().isBefore(now)) {
            throw new BusinessConflictException(ErrorCode.BOOKING_TOKEN_EXPIRED);
        }

        // Pessimistic write lock on slot
        InterviewBookingSlot slot = interviewBookingSlotRepository
                .findByIdWithDetailsForUpdate(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_SLOT_NOT_FOUND, request.getSlotId()));

        if (!slot.getBookingRequest().getId().equals(bookingRequest.getId())) {
            throw new BadRequestException(ErrorCode.BOOKING_SLOT_NOT_FOUND);
        }

        if (slot.getStatus() == InterviewBookingSlotStatus.CONFIRMED) {
            throw new BusinessConflictException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
        }
        if (slot.getStatus() == InterviewBookingSlotStatus.HELD && slot.getHeldUntil() != null && slot.getHeldUntil().isAfter(now)) {
            throw new BusinessConflictException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
        }

        User interviewer = bookingRequest.getInterviewer();
        boolean hasConflict = interviewParticipantRepository
                .existsByInterviewer_IdAndInterview_InterviewDateAndInterview_InterviewTimeAndInterview_StatusNot(
                        interviewer.getId(), slot.getSlotDate(), slot.getSlotTime(), InterviewStatus.CANCELLED);
        if (hasConflict) {
            throw new BusinessConflictException(ErrorCode.INTERVIEWER_TIME_CONFLICT, interviewer.getFullName());
        }

        // Confirm slot & mark request completed
        slot.setStatus(InterviewBookingSlotStatus.CONFIRMED);
        slot.setSelectedAt(now);
        interviewBookingSlotRepository.save(slot);

        bookingRequest.setStatus(InterviewBookingRequestStatus.COMPLETED);
        interviewBookingRequestRepository.save(bookingRequest);

        Application application = bookingRequest.getApplication();
        PipelineStage targetStage = bookingRequest.getTargetStage();
        PipelineStage fromStage = application.getCurrentStage();

        if (targetStage != null && !targetStage.getId().equals(fromStage.getId())) {
            application.setCurrentStage(targetStage);
            application.setStatus(ApplicationStatus.IN_PROGRESS);
            application.setLastStageChangedAt(now);
            application.setSlaAlertSentAt(null); // UC-41: fresh stage-dwell, un-alerted
            application.setUpdatedAt(now);
            applicationRepository.save(application);

            ApplicationStageHistory history = ApplicationStageHistory.builder()
                    .application(application)
                    .fromStage(fromStage)
                    .toStage(targetStage)
                    .changedBy(bookingRequest.getCreatedBy())
                    .transitionType(StageTransitionType.SYSTEM)
                    .changedAt(now)
                    .build();
            applicationStageHistoryRepository.save(history);
        }

        // Handle Google Meet link if ONLINE
        String effectiveLocationOrLink = bookingRequest.getLocationOrLink();
        if (bookingRequest.getMode() == InterviewMode.ONLINE) {
            if (effectiveLocationOrLink == null || effectiveLocationOrLink.isBlank()) {
                String summary = String.format("Phỏng vấn %s - %s",
                        application.getCandidate().getFullName(),
                        application.getJobPosition().getTitle());
                String description = String.format("Phỏng vấn tuyển dụng vị trí %s cho ứng viên %s",
                        application.getJobPosition().getTitle(),
                        application.getCandidate().getFullName());
                LocalDateTime start = LocalDateTime.of(slot.getSlotDate(), slot.getSlotTime());
                LocalDateTime end = start.plusMinutes(slot.getDurationMinutes());

                List<String> attendeeEmails = new ArrayList<>();
                if (interviewer.getEmail() != null && !interviewer.getEmail().isBlank()) {
                    attendeeEmails.add(interviewer.getEmail());
                }
                if (application.getCandidate().getPrimaryEmail() != null && !application.getCandidate().getPrimaryEmail().isBlank()) {
                    attendeeEmails.add(application.getCandidate().getPrimaryEmail());
                }

                effectiveLocationOrLink = calendarIntegrationService.createGoogleMeetMeeting(
                                summary, description, start, end, attendeeEmails)
                        .orElseGet(InterviewService::generateGoogleMeetLink);
            }
        }

        // Cancel previous scheduled interviews
        List<Interview> existingInterviews = interviewRepository.findAllByApplication_IdAndStatus(
                application.getId(), InterviewStatus.SCHEDULED);
        for (Interview old : existingInterviews) {
            old.setStatus(InterviewStatus.CANCELLED);
            old.setUpdatedAt(now);
            interviewRepository.save(old);
        }

        // Persist interview
        Interview interview = Interview.builder()
                .application(application)
                .scheduledBy(bookingRequest.getCreatedBy())
                .interviewDate(slot.getSlotDate())
                .interviewTime(slot.getSlotTime())
                .mode(bookingRequest.getMode())
                .locationOrLink(effectiveLocationOrLink)
                .status(InterviewStatus.SCHEDULED)
                .notes(request.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();
        interview = interviewRepository.save(interview);

        InterviewParticipant participant = InterviewParticipant.builder()
                .interview(interview)
                .interviewer(interviewer)
                .createdAt(now)
                .build();
        interviewParticipantRepository.save(participant);

        // Outbox event EM-07 for candidate
        String candidateEmail = application.getCandidate().getPrimaryEmail();
        String candidateName = application.getCandidate().getFullName();
        String jobTitle = application.getJobPosition().getTitle();
        String formattedDate = slot.getSlotDate().format(DATE_FORMATTER);
        String formattedTime = slot.getSlotTime().format(TIME_FORMATTER);

        outboxEventPublisher.publish(
                OutboxEventType.BOOKING_CONFIRMED_EMAIL,
                OutboxPayloads.bookingConfirmedEmail(
                        candidateEmail,
                        candidateName,
                        jobTitle,
                        formattedDate,
                        formattedTime,
                        effectiveLocationOrLink,
                        bookingRequest.getMode() != null ? bookingRequest.getMode().name() : "ONLINE"
                )
        );

        // Outbox event EM-08 for interviewer
        outboxEventPublisher.publish(
                OutboxEventType.INTERVIEWER_ASSIGNED_EMAIL,
                OutboxPayloads.interviewerAssignedEmail(
                        interviewer.getEmail(),
                        interviewer.getFullName(),
                        candidateName,
                        jobTitle,
                        formattedDate,
                        formattedTime,
                        effectiveLocationOrLink
                )
        );

        log.info("Booking slot {} confirmed for token {} -> created interview {}",
                slot.getId(), token, interview.getId());

        return BookingConfirmResponseDto.builder()
                .interviewId(interview.getId())
                .interviewDate(slot.getSlotDate())
                .interviewTime(slot.getSlotTime())
                .durationMinutes(slot.getDurationMinutes())
                .mode(bookingRequest.getMode())
                .locationOrLink(effectiveLocationOrLink)
                .interviewerName(interviewer.getFullName())
                .jobTitle(jobTitle)
                .candidateName(candidateName)
                .message("Interview confirmed successfully")
                .build();
    }

    /**
     * Retrieves all booking requests created for an application.
     */
    public List<BookingRequestResponseDto> getBookingRequestsForApplication(UUID applicationId, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW, ResourceContext.none());
        return interviewBookingRequestRepository.findByApplication_IdOrderByCreatedAtDesc(applicationId)
                .stream()
                .map(r -> BookingRequestResponseDto.builder()
                        .id(r.getId())
                        .bookingToken(r.getBookingToken())
                        .bookingLink(String.format("%s/%s", bookingLinkBaseUrl.replaceAll("/$", ""), r.getBookingToken()))
                        .dateRangeStart(r.getDateRangeStart())
                        .dateRangeEnd(r.getDateRangeEnd())
                        .expiresAt(r.getExpiresAt())
                        .status(r.getStatus())
                        .interviewerId(r.getInterviewer().getId())
                        .interviewerName(r.getInterviewer().getFullName())
                        .totalSlots(r.getSlots() != null ? r.getSlots().size() : 0)
                        .build())
                .toList();
    }
}

