package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Returned so the FE/Postman can inspect what info and roles the current
 * token carries - useful when debugging the Keycloak integration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserResponseDto {
    private String keycloakId;
    private String username;
    private String email;
    private String fullName;
    private Set<String> roles;
}
