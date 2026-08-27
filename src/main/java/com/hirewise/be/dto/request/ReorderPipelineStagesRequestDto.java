package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for {@code PIPELINE_MANAGE} - reorders every Stage of a
 * Pipeline Template in one shot (UC-05). {@code stageIds} must be the
 * FULL, exact set of stage ids currently in the template, listed in the
 * desired new order - the service assigns {@code position = index + 1}
 * for each (BR-PIPE-04: contiguous ascending integers, updated together in
 * one transaction).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderPipelineStagesRequestDto {

    @NotEmpty(message = "{validation.pipeline_stage.reorder.stage_ids.required}")
    private List<Long> stageIds;
}
