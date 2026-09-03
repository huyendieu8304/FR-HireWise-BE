package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO returned after successfully scheduling an interview (UC-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleInterviewResponseDto {

    private UUID interviewId;
    private UUID applicationId;
    private LocalDate interviewDate;
    private LocalTime interviewTime;
    private InterviewMode mode;
    private String locationOrLink;
    private InterviewStatus status;
    private String notes;
    private List<InterviewParticipantDto> participants;

    // Stage transition info (matches MoveApplicationStageResponseDto)
    private Long fromStageId;
    private Long toStageId;
    private ApplicationStatus applicationStatus;
    private Instant lastStageChangedAt;
}
