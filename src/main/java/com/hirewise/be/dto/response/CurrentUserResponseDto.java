package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Hồ sơ định danh/quyền của user hiện tại, trả về trong {@code user} của
 * {@code POST /api/auth/login} (và {@code POST /api/auth/google}) — dùng để
 * FE dựng {@code AuthUser} lưu vào {@code useAuthStore} mà KHÔNG cần tự suy
 * ra quyền hạn từ {@code roles} nữa.
 * <p>
 * {@code permissions} là tập permission code đã resolve sẵn từ TOÀN BỘ role
 * hiện tại của user (hợp của {@code RolePermissionCache.permissionsOf(role)}
 * cho từng role trong {@code roles} — xem {@code AuthService#issueLoginResponse}),
 * cùng nguồn dữ liệu {@code role_permissions} mà {@code AccessControlService}
 * dùng để enforce ở tầng API. FE chỉ nên dùng field này để ẩn/hiện UI
 * (menu, nút bấm...) — ranh giới bảo mật thật sự vẫn luôn là
 * {@code AccessControlService.checkAccess(...)} ở backend.
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
