package com.hirewise.be.dto.response;

import com.hirewise.be.domain.InterviewMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Public response returned after candidate successfully confirms a booking slot (UC-35).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingConfirmResponseDto {

    private UUID interviewId;
    private LocalDate interviewDate;
    private LocalTime interviewTime;
    private int durationMinutes;
    private InterviewMode mode;
    private String locationOrLink;
    private String interviewerName;
    private String jobTitle;
    private String candidateName;
    private String message;
}
