package com.hirewise.be.security;

import java.util.Set;

/**
 * View gọn của user hiện tại - CHỈ chứa những field thực sự có nơi tiêu
 * thụ trong code (controller, service, RBAC layer 2-4). Dựng từ claim của
 * JWT (Keycloak access token) để controller/service không phải tự đào bới
 * claim thô mỗi lần cần thông tin user.
 *
 * userId KHÔNG đến từ JWT - được AuthenticationFreshnessFilter (RBAC layer
 * 1) nạp từ bảng users nội bộ (qua UserDirectoryService, có cache) rồi
 * CurrentUserResolver đọc lại từ request attribute
 * (UserSnapshot.REQUEST_ATTRIBUTE) để gắn vào đây. Luôn có giá trị (không
 * null) đối với mọi request đã qua được layer 1, vì filter đó đã từ chối
 * request nếu tài khoản chưa được cấp phát trong hệ thống.
 *
 */
public record CurrentUser(
        String keycloakId,      // claim "sub" - id định danh duy nhất bên Keycloak
        String username,        // claim "preferred_username"
        String email,            // claim "email"
        String fullName,         // claim "name"
        Set<String> roles,       // roles đã "giải nén" từ realm_access/resource_access, chữ hoa, không có tiền tố ROLE_
        Long userId              // id nội bộ (bảng users) - dùng làm owner id cho RBAC layer 4 (Ownership)
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role.toUpperCase());
    }
}
