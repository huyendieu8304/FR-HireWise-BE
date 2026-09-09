package com.hirewise.be.dto.response;

import com.hirewise.be.domain.InterviewBookingRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO returned to Recruiter after creating/sending a booking request (UC-25).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestResponseDto {

    private Long id;
    private UUID bookingToken;
    private String bookingLink;
    private LocalDate dateRangeStart;
    private LocalDate dateRangeEnd;
    private Instant expiresAt;
    private InterviewBookingRequestStatus status;
    private Long interviewerId;
    private String interviewerName;
    private int totalSlots;
}
