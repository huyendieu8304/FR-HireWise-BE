package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 1 row of the Scorecard configuration checklist shown on the Job Approval
 * detail screen (UC-14/15 hard gate) - 1 per {@code INTERVIEW}-type Stage of
 * the Job's pipeline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewStageScorecardStatusDto {
    private Long pipelineStageId;
    private String stageName;
    private int position;
    private boolean configured;
    /** {@code null} nếu {@code configured = false}. */
    private UUID jobStageScorecardId;
}
