package com.hirewise.be.dto.response;

import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for displaying scheduled interviews on the calendar view (UC-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewCalendarDto {
    private UUID interviewId;
    private UUID applicationId;
    private String candidateName;
    private String candidateEmail;
    private String jobTitle;
    private LocalDate interviewDate;
    private LocalTime interviewTime;
    private InterviewMode mode;
    private String locationOrLink;
    private InterviewStatus status;
    private List<String> interviewerNames;
    private String notes;
}
