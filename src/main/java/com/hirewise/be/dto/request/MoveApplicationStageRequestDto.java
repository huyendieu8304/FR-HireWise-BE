package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for UC-23: drag-and-drop an Application into a different
 * Kanban column (Pipeline Stage).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveApplicationStageRequestDto {

    @NotNull(message = "{validation.application_stage.target_stage_id.required}")
    private Long targetStageId;
}
