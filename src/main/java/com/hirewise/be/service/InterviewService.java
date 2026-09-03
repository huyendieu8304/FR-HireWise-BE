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
import com.hirewise.be.repository.InterviewParticipantRepository;
import com.hirewise.be.repository.InterviewRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for UC-24: Schedule predetermined interview (fixed schedule).
 * <p>
 * Handles interview creation, participant assignment, moving the application
 * to the INTERVIEW pipeline stage, and enqueuing notification emails (EM-05 for
 * the candidate, EM-08 for each assigned interviewer).
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class InterviewService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    InterviewRepository interviewRepository;
    InterviewParticipantRepository interviewParticipantRepository;
    ApplicationRepository applicationRepository;
    ApplicationStageHistoryRepository applicationStageHistoryRepository;
    PipelineStageRepository pipelineStageRepository;
    UserRepository userRepository;
    OutboxEventPublisher outboxEventPublisher;
    AccessControlService accessControlService;
    CalendarIntegrationService calendarIntegrationService;
    Clock clock;

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

        // Validate and fetch interviewers
        List<User> interviewers = new ArrayList<>();
        for (Long interviewerId : request.getInterviewerIds()) {
            User interviewer = userRepository.findById(interviewerId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTERVIEW_INTERVIEWER_NOT_FOUND, interviewerId));
            if (interviewer.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessConflictException(ErrorCode.INTERVIEW_INTERVIEWER_INACTIVE, interviewer.getFullName());
            }
            interviewers.add(interviewer);
        }

        Instant now = Instant.now(clock);

        // 1. Move stage (audit trail)
        application.setCurrentStage(toStage);
        application.setStatus(ApplicationStatus.IN_PROGRESS);
        application.setLastStageChangedAt(now);
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
                effectiveLocationOrLink = calendarIntegrationService.createGoogleMeetMeeting(summary, description, start, end)
                        .orElseGet(InterviewService::generateGoogleMeetLink);
            }
        }

        User scheduledByUser = userRepository.getReferenceById(currentUser.userId());
        Interview interview = Interview.builder()
                .application(application)
                .scheduledBy(scheduledByUser)
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
     * Retrieves scheduled interviews within a date range for the calendar visual grid (UC-24).
     */
    public List<com.hirewise.be.dto.response.InterviewCalendarDto> getScheduleCalendar(
            LocalDate startDate, LocalDate endDate, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW, ResourceContext.none());
        List<Interview> interviews = interviewRepository.findBetweenDates(startDate, endDate);
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
                    .build();
        }).toList();
    }

    /**
     * Generates a Jitsi Meet room URL as fallback when Google Calendar is not connected.
     * Jitsi Meet rooms are publicly accessible and require no prior API creation.
     * Format: https://meet.jit.si/HireWise-{random-adjective}-{random-noun}-{random-number}
     */
    public static String generateGoogleMeetLink() {
        String[] adjectives = {"Swift", "Bright", "Clear", "Smart", "Prime", "Sharp", "Bold", "Fast"};
        String[] nouns = {"Interview", "Meeting", "Session", "Review", "Panel", "Talk", "Chat", "Call"};
        java.util.Random rnd = new java.security.SecureRandom();
        String adj = adjectives[rnd.nextInt(adjectives.length)];
        String noun = nouns[rnd.nextInt(nouns.length)];
        int num = 1000 + rnd.nextInt(9000);
        return "https://meet.jit.si/HireWise-" + adj + "-" + noun + "-" + num;
    }
}
