package com.hirewise.be.dto.request;

import com.hirewise.be.domain.InterviewMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Request body for generating and sending a self-service booking link to a candidate (UC-25).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendBookingLinkRequestDto {

    @NotNull(message = "{validation.booking.interviewer_id.not_null}")
    private Long interviewerId;

    @NotNull(message = "{validation.booking.date_range_start.not_null}")
    private LocalDate dateRangeStart;

    @NotNull(message = "{validation.booking.date_range_end.not_null}")
    private LocalDate dateRangeEnd;

    private Long targetStageId;

    @NotNull(message = "{validation.booking.mode.not_null}")
    private InterviewMode mode;

    private String locationOrLink;

    @NotEmpty(message = "{validation.booking.slots.not_empty}")
    @Valid
    private List<SlotItemDto> slots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SlotItemDto {
        @NotNull(message = "{validation.booking.slot_date.not_null}")
        private LocalDate slotDate;

        @NotNull(message = "{validation.booking.slot_time.not_null}")
        private LocalTime slotTime;

        @Builder.Default
        private Integer durationMinutes = 45;
    }
}
