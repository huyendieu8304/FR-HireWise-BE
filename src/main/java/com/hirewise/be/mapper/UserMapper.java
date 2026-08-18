package com.hirewise.be.mapper;

import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.dto.response.UserAccessScopeResponseDto;
import com.hirewise.be.dto.response.UserResponseDto;

import java.util.Set;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponseDto toResponseDto(User entity, Set<String> roleCodes) {
        return UserResponseDto.builder()
                .id(entity.getId())
                .keycloakId(entity.getKeycloakId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .status(entity.getStatus())
                .roleCodes(roleCodes)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static UserAccessScopeResponseDto toResponseDto(UserAccessScope entity) {
        return UserAccessScopeResponseDto.builder()
                .id(entity.getId())
                .scopeType(entity.getScopeType())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .jobId(entity.getJobId())
                .includeSubDepartments(entity.isIncludeSubDepartments())
                .canWrite(entity.isCanWrite())
                .validFrom(entity.getValidFrom())
                .validTo(entity.getValidTo())
                .build();
    }
}
