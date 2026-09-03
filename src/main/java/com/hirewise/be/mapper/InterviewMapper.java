package com.hirewise.be.mapper;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Interview;
import com.hirewise.be.domain.InterviewParticipant;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.response.InterviewParticipantDto;
import com.hirewise.be.dto.response.InterviewerOptionDto;
import com.hirewise.be.dto.response.ScheduleInterviewResponseDto;

import java.util.List;

/**
 * Mapper for Interview entities and DTOs (UC-24).
 */
public final class InterviewMapper {

    private InterviewMapper() {
    }

    public static ScheduleInterviewResponseDto toScheduleResponseDto(
            Interview interview,
            List<InterviewParticipant> participants,
            Application application,
            Long fromStageId) {

        List<InterviewParticipantDto> participantDtos = participants.stream()
                .map(InterviewMapper::toParticipantDto)
                .toList();

        return ScheduleInterviewResponseDto.builder()
                .interviewId(interview.getId())
                .applicationId(application.getId())
                .interviewDate(interview.getInterviewDate())
                .interviewTime(interview.getInterviewTime())
                .mode(interview.getMode())
                .locationOrLink(interview.getLocationOrLink())
                .status(interview.getStatus())
                .notes(interview.getNotes())
                .participants(participantDtos)
                .fromStageId(fromStageId)
                .toStageId(application.getCurrentStage().getId())
                .applicationStatus(application.getStatus())
                .lastStageChangedAt(application.getLastStageChangedAt())
                .build();
    }

    public static InterviewParticipantDto toParticipantDto(InterviewParticipant participant) {
        return InterviewParticipantDto.builder()
                .interviewerId(participant.getInterviewer().getId())
                .interviewerName(participant.getInterviewer().getFullName())
                .interviewerEmail(participant.getInterviewer().getEmail())
                .build();
    }

    public static InterviewerOptionDto toInterviewerOptionDto(User user) {
        return InterviewerOptionDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .build();
    }
}
