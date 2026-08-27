package com.hirewise.be.mapper;

import com.hirewise.be.domain.Department;
import com.hirewise.be.dto.response.DepartmentResponseDto;

/**
 * Converts {@link Department} entities into their response DTO.
 */
public final class DepartmentMapper {

    private DepartmentMapper() {
    }

    /**
     * Converts a {@link Department} entity into its response DTO.
     *
     * @param entity department entity to convert
     * @return the corresponding response DTO
     */
    public static DepartmentResponseDto toResponseDto(Department entity) {
        return DepartmentResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }
}
