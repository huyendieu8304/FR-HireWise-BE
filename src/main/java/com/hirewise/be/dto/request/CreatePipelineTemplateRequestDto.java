package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PIPELINE_MANAGE} - creates a new Pipeline
 * Template (UC-04 AF-01). New templates always start in {@code DRAFT}
 * status - BR-PIPE-01 (>= 2 stages, including one terminal-success and one
 * terminal-rejected stage) is a precondition for moving a template to
 * {@code ACTIVE}, a different action not covered by this request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePipelineTemplateRequestDto {

    @NotBlank(message = "{validation.pipeline_template.name.required}")
    @Size(max = 150, message = "{validation.pipeline_template.name.size}")
    private String name;

    /** {@code null} = company-wide template, shared by every department (UC-04 AF-01). */
    private Long departmentId;
}
