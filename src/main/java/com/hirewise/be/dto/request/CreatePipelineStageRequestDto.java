package com.hirewise.be.dto.request;

import com.hirewise.be.domain.StageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PIPELINE_MANAGE} - appends a new Stage to an
 * existing Pipeline Template (UC-04 main flow steps 2-4). {@code position}
 * is deliberately NOT part of this request - the service always appends
 * the new stage at the end (BR-PIPE-04); reordering existing stages is
 * UC-05, a separate endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePipelineStageRequestDto {

    @NotBlank(message = "{validation.pipeline_stage.name.required}")
    @Size(max = 50, message = "{validation.pipeline_stage.name.size}")
    private String name;

    // BR-PIPE-02: unique per template (checked in the service, not here); uppercase/
    // no-accent technical code per the UC-04 Screen Description.
    @NotBlank(message = "{validation.pipeline_stage.code.required}")
    @Size(max = 50, message = "{validation.pipeline_stage.code.size}")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "{validation.pipeline_stage.code.pattern}")
    private String code;

    @NotNull(message = "{validation.pipeline_stage.stage_type.required}")
    private StageType stageType;

    /** "Is Terminal" checkbox (UC-04 Screen Description) - forced true server-side for TERMINAL_* stage types. */
    private boolean terminal;

    @Positive(message = "{validation.pipeline_stage.sla_hours.positive}")
    private Integer slaHours;
}
