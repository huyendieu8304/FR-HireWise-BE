package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for UC-09 normal flow (create email template).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmailTemplateRequestDto {

    @NotBlank(message = "{validation.email_template.name.required}")
    @Size(max = 150, message = "{validation.email_template.name.size}")
    private String name;

    /**
     * Stable code (e.g. "EM-01"). BR-EMAILTPL-01: must be unique system-wide.
     */
    @NotBlank(message = "{validation.email_template.code.required}")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,50}$", message = "{validation.email_template.code.pattern}")
    private String code;

    /** Optional; links the template to a pipeline stage so it fires automatically. */
    private Long pipelineStageId;

    /** May contain dynamic variables like {{Candidate_Name}} (BR-EMAILTPL-02). */
    @NotBlank(message = "{validation.email_template.subject.required}")
    @Size(max = 255, message = "{validation.email_template.subject.size}")
    private String subjectTemplate;

    @NotBlank(message = "{validation.email_template.body.required}")
    private String bodyTemplate;
}