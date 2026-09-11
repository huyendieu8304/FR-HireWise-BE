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
 * Request body for {@code PIPELINE_MANAGE} - edits an existing Stage's full
 * structure (name/code/type/terminal flag/SLA), not just its SLA (see
 * {@link UpdateStageSlaRequestDto} for that narrower endpoint). Same shape
 * and validation as {@link CreatePipelineStageRequestDto} - a Stage's
 * fields don't change meaning just because it already exists. Only ever
 * reachable while the parent Pipeline Template is still {@code DRAFT}
 * ({@code PipelineService#requireTemplateEditable}); a template that has
 * reached {@code ACTIVE} may already have a Job relying on this exact
 * structure, so no Stage can be edited (or created/reordered/deleted) past
 * that point.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStageRequestDto {

    @NotBlank(message = "{validation.pipeline_stage.name.required}")
    @Size(max = 50, message = "{validation.pipeline_stage.name.size}")
    private String name;

    // BR-PIPE-02: unique per template excluding this stage itself (checked in the
    // service, not here); uppercase/no-accent technical code per the UC-04 Screen Description.
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
