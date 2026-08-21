package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Returned by {@code GET /api/me} so the FE can inspect what the backend
 * resolved for the caller's identity/roles from their access token.
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
}
