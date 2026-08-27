package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmailTemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response body for a single email template (UC-09/UC-10).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplateResponseDto {
    private Long id;
    private String code;
    private String name;
    private Long pipelineStageId;
    private String pipelineStageName;
    private String subjectTemplate;
    private String bodyTemplate;
    private int version;
    private EmailTemplateStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}