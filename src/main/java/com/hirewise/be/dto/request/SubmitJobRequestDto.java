package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code JOB_SUBMIT} (UC-13 normal flow) - attaches a
 * Pipeline Template to a Draft/Rejected Job Position and submits it for
 * approval.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitJobRequestDto {

    // EX-01/ME-18: "Chưa chọn Pipeline Template" blocks submission outright.
    @NotNull(message = "{validation.job_position.pipeline_template_id.required}")
    private Long pipelineTemplateId;
}
