package com.hirewise.be.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code SLA_CONFIGURE} (US-MGR-04, UC-40) - sets or clears
 * the SLA threshold of one existing Stage. {@code null} clears it (the Stage
 * goes back to "no limit"); a present value must be positive, same rule as
 * {@link CreatePipelineStageRequestDto#getSlaHours()} at creation time.
 * <p>
 * Deliberately its own narrow endpoint rather than reusing the Stage's full
 * create payload: there is no general "edit a Stage" endpoint yet (name/code/
 * type are effectively immutable after creation - UC-04/05/06 never needed
 * one), and this ticket only ever needs to change one field, from a role
 * ({@code HIRING_MANAGER}) that must NOT gain the broader
 * {@code PIPELINE_MANAGE} capability (create/reorder/delete stages).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStageSlaRequestDto {

    @Positive(message = "{validation.pipeline_stage.sla_hours.positive}")
    private Integer slaHours;
}
