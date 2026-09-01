package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-29 main flow: request body for {@code POST /api/applications/{applicationId}/reject}.
 * <p>
 * BR-REJ-01: {@code reasonId} must reference an active row in the
 * standardized {@code rejection_reasons} catalog (see
 * {@code GET /api/rejection-reasons}) - free-typing a reason is not
 * allowed. {@code customMessage} is an optional note added on top of it,
 * rendered into the auto-reject email (UC-30, EM-09 {{Custom_Message_Block}}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectApplicationRequestDto {

    @NotNull(message = "{validation.application_reject.reason_id.required}")
    private Long reasonId;

    @Size(max = 500, message = "{validation.application_reject.custom_message.size}")
    private String customMessage;
}
