package com.hirewise.be.mapper;

import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.dto.response.UserAccessScopeResponseDto;
import com.hirewise.be.dto.response.UserResponseDto;

import java.util.Set;

/**
 * Converts {@link User} and {@link UserAccessScope} entities into their response DTOs.
 */
public final class UserMapper {

    private UserMapper() {
    }

    /**
     * Converts a {@link User} entity into its response DTO.
     * <p>
     * Role codes are not resolved here - the caller must already have looked them up
     * (from {@code user_roles}) and pass them in, to keep this mapper limited to plain
     * entity-to-DTO conversion.
     *
     * @param entity    user entity to convert
     * @param roleCodes role codes assigned to the user, resolved by the caller
     * @return the corresponding response DTO; department fields are {@code null}
     *         when the user has no department assigned
     */
    public static UserResponseDto toResponseDto(User entity, Set<String> roleCodes) {
        return UserResponseDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .departmentId(entity.getDepartment() != null ? entity.getDepartment().getId() : null)
                .departmentName(entity.getDepartment() != null ? entity.getDepartment().getName() : null)
                .status(entity.getStatus())
                .roleCodes(roleCodes)
                .lastAuthenticatedAt(entity.getLastAuthenticatedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Converts a {@link UserAccessScope} entity into its response DTO.
     *
     * @param entity access scope entity to convert
     * @return the corresponding response DTO; department fields are {@code null}
     *         when the scope is not tied to a specific department
     */
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
