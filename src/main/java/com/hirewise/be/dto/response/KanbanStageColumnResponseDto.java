package com.hirewise.be.dto.response;

import com.hirewise.be.domain.StageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One Kanban column (UC-22) - an active Pipeline Stage plus every
 * Application currently sitting in it ({@code current_stage_id}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KanbanStageColumnResponseDto {
    private Long stageId;
    private String name;
    private String code;
    private StageType stageType;
    private int position;
    private boolean terminal;
    private List<ApplicationCardResponseDto> applications;
}
