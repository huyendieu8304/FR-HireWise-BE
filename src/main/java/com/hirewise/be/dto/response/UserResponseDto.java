package com.hirewise.be.dto.response;

import com.hirewise.be.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {
    private Long id;
    private String keycloakId;
    private String email;
    private String fullName;
    private Long departmentId;
    private String departmentName;
    private UserStatus status;
    private Set<String> roleCodes;
    private Instant createdAt;
    private Instant updatedAt;
}
