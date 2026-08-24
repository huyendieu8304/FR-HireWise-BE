package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * backend resolved for the caller's identity/roles/permission
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserResponseDto {
    private Long userId;
    private String email;
    private String fullName;
    private Set<String> roles;
    private Set<String> permissions;
}
