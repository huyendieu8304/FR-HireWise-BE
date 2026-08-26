package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * UC-22: the full Kanban board for one Job Position - every active Stage
 * of its assigned Pipeline Template, in column (position) order, each
 * carrying the Applications currently at that Stage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KanbanBoardResponseDto {
    private UUID jobId;
    private String jobTitle;
    private List<KanbanStageColumnResponseDto> columns;
}
