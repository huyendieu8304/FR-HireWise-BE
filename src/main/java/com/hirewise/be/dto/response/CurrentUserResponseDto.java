package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Trả về để FE/Postman kiểm tra token đang mang thông tin/role gì -
 * hữu ích khi debug tích hợp Keycloak.
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
