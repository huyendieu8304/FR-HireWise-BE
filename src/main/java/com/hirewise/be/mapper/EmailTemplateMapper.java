package com.hirewise.be.mapper;

import com.hirewise.be.domain.EmailTemplate;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.dto.response.EmailTemplateResponseDto;
import com.hirewise.be.dto.response.PipelineStageResponseDto;

/**
 * Converts EmailTemplate and PipelineStage entities into their response DTOs for UC-09.
 */
public final class EmailTemplateMapper {

    private EmailTemplateMapper() {
    }

    /**
     * Converts an EmailTemplate entity to its response DTO.
     * Stage name/id are null when no stage is linked.
     */
    public static EmailTemplateResponseDto toResponseDto(EmailTemplate entity) {
        return EmailTemplateResponseDto.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .pipelineStageId(entity.getPipelineStage() != null ? entity.getPipelineStage().getId() : null)
                .pipelineStageName(entity.getPipelineStage() != null ? entity.getPipelineStage().getName() : null)
                .subjectTemplate(entity.getSubjectTemplate())
                .bodyTemplate(entity.getBodyTemplate())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts a PipelineStage entity to the lightweight DTO for the dropdown.
     */
    public static PipelineStageResponseDto toResponseDto(PipelineStage entity) {
        return PipelineStageResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .stageType(entity.getStageType())
                .position(entity.getPosition())
                .pipelineTemplateName(entity.getPipelineTemplate() != null
                        ? entity.getPipelineTemplate().getName() : null)
                .build();
    }
}