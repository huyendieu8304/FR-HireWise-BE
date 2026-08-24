package com.hirewise.be.mapper;

import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.PipelineTemplate;
import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.dto.response.PipelineTemplateResponseDto;

/**
 * Converts {@link PipelineTemplate} and {@link PipelineStage} entities into
 * their response DTOs.
 */
public final class PipelineMapper {

    private PipelineMapper() {
    }

    /**
     * Converts a {@link PipelineTemplate} entity into its response DTO.
     *
     * @param entity template entity to convert
     * @return the corresponding response DTO; department fields are
     *         {@code null} for a company-wide template
     */
    public static PipelineTemplateResponseDto toResponseDto(PipelineTemplate entity) {
        return PipelineTemplateResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts a {@link PipelineStage} entity into its response DTO.
     *
     * @param entity stage entity to convert
     * @return the corresponding response DTO
     */
    public static PipelineStageResponseDto toResponseDto(PipelineStage entity) {
        return PipelineStageResponseDto.builder()
                .id(entity.getId())
                .pipelineTemplateId(entity.getPipelineTemplate().getId())
                .name(entity.getName())
                .code(entity.getCode())
                .stageType(entity.getStageType())
                .position(entity.getPosition())
                .terminal(entity.isTerminal())
                .slaHours(entity.getSlaHours())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
