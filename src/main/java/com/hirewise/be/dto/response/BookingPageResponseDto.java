package com.hirewise.be.dto.response;

import com.hirewise.be.domain.InterviewBookingRequestStatus;
import com.hirewise.be.domain.InterviewBookingSlotStatus;
import com.hirewise.be.domain.InterviewMode;
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
 * Public response returned to candidates viewing the booking page (UC-34).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingPageResponseDto {

    private UUID bookingToken;
    private String candidateName;
    private String jobTitle;
    private String interviewerName;
    private InterviewMode mode;
    private String locationOrLink;
    private LocalDate dateRangeStart;
    private LocalDate dateRangeEnd;
    private Instant expiresAt;
    private InterviewBookingRequestStatus status;
    private List<BookingSlotDto> slots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BookingSlotDto {
        private Long id;
        private LocalDate slotDate;
        private LocalTime slotTime;
        private int durationMinutes;
        private InterviewBookingSlotStatus status;
        private boolean available;
        private String unavailableReason;
    }
}
