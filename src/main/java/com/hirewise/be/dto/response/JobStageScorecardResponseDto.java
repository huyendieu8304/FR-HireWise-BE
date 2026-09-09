package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ScorecardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The real per-(Job, Stage) Scorecard scoring definition (UC-27/28). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStageScorecardResponseDto {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private Long pipelineStageId;
    private String stageName;
    private String name;
    private int version;
    private ScorecardStatus status;
    /** {@code null} nếu tạo hoàn toàn từ đầu, không dựa trên Master Template nào. */
    private UUID sourceMasterTemplateId;
    private String sourceMasterTemplateName;
    private List<ScorecardCriterionResponseDto> criteria;
    private Instant createdAt;
    private Instant updatedAt;
}
