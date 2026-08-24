package com.hirewise.be.dto.response;

import com.hirewise.be.domain.StageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response body representing one Stage (Kanban column) of a Pipeline Template.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStageResponseDto {
    private Long id;
    private Long pipelineTemplateId;
    private String name;
    private String code;
    private StageType stageType;
    private int position;
    private boolean terminal;
    private Integer slaHours;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
