package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for UC-09 AF-01 (edit email template).
 * When subjectTemplate or bodyTemplate change, version is incremented (BR-EMAILTPL-04).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmailTemplateRequestDto {

    @NotBlank(message = "{validation.email_template.name.required}")
    @Size(max = 150, message = "{validation.email_template.name.size}")
    private String name;

    /**
     * BR-EMAILTPL-01: code must remain unique; service uses excludeSelf query.
     */
    @NotBlank(message = "{validation.email_template.code.required}")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]{1,50}$", message = "{validation.email_template.code.pattern}")
    private String code;

    /** Pass null to detach the template from any stage. */
    private Long pipelineStageId;

    @NotBlank(message = "{validation.email_template.subject.required}")
    @Size(max = 255, message = "{validation.email_template.subject.size}")
    private String subjectTemplate;

    @NotBlank(message = "{validation.email_template.body.required}")
    private String bodyTemplate;
}