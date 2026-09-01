package com.hirewise.be.dto.response;

import com.hirewise.be.domain.StageTransitionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * UC-20: one entry of an Application's stage-change timeline
 * ({@code application_stage_history}, BR-KANBAN-01). {@code fromStageId}/
 * {@code fromStageName} are {@code null} for the very first ("New") event.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationStageHistoryResponseDto {
    private Long fromStageId;
    private String fromStageName;
    private Long toStageId;
    private String toStageName;
    private StageTransitionType transitionType;
    private String changedByName;
    private Instant changedAt;
}
