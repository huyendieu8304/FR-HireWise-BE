package com.hirewise.be.security;

import java.util.Set;

/**
 * View gọn của user hiện tại, được dựng từ claim của JWT (Keycloak access
 * token) để controller/service không phải tự đào bới claim thô mỗi lần
 * cần thông tin user.
 */
public record CurrentUser(
        String keycloakId,      // claim "sub" - id định danh duy nhất bên Keycloak
        String username,        // claim "preferred_username"
        String email,            // claim "email"
        String fullName,         // claim "name"
        Set<String> roles        // roles đã "giải nén" từ realm_access/resource_access, chữ hoa, không có tiền tố ROLE_
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role.toUpperCase());
    }
}
