package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-23: result of a Kanban drag-and-drop stage change - lets the FE
 * reconcile its optimistic UI update with the authoritative timestamp/
 * status computed by the server.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveApplicationStageResponseDto {
    private UUID applicationId;
    private Long fromStageId;
    private Long toStageId;
    private ApplicationStatus status;
    private Instant lastStageChangedAt;
}
