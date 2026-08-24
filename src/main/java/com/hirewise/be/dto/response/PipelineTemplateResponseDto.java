package com.hirewise.be.dto.response;

import com.hirewise.be.domain.PipelineTemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response body representing a Pipeline Template.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineTemplateResponseDto {
    private Long id;
    private String name;
    private Long departmentId;
    private String departmentName;
    private PipelineTemplateStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
