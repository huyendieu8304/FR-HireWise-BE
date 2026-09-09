package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for candidate confirming a selected booking slot (UC-35).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmBookingSlotRequestDto {

    @NotNull(message = "{validation.booking.slot_id.not_null}")
    private Long slotId;

    private String notes;
}
