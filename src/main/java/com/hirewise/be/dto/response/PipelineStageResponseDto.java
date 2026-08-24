package com.hirewise.be.dto.response;

import com.hirewise.be.domain.StageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight pipeline stage projection for the Email Template form dropdown (UC-09 step 2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStageResponseDto {
    private Long id;
    private String name;
    private String code;
    private StageType stageType;
    private int position;
    /** Name of the parent pipeline template - helps disambiguate stages with the same name. */
    private String pipelineTemplateName;
}