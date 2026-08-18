package com.hirewise.be.authorization;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Bảng quản lý chính sách Ownership (Access Policy Table).
 *
 * Định nghĩa quy tắc: Cùng 1 Permission, vai trò (Role) này có thể YÊU CẦU phải là Owner mới được làm,
 * nhưng vai trò khác lại KHÔNG CẦN (ví dụ: Recruiter sửa Job thì phải là Owner của Job đó,
 * nhưng Hiring Manager hoặc Giám đốc duyệt Job thì không cần phải là Owner).
 */
@Component
public class OwnershipPolicyRegistry {

    /** Key dùng để tra cứu Ma trận Policy: cặp (PermissionCode, RoleCode) */
    private record Key(String permissionCode, String roleCode) {
    }

    /**
     * Ma trận cấu hình tĩnh các quy tắc Ownership:
     * - Key: (Permission, Role)
     * - Value: true = Bắt buộc phải là Owner, false = Không cần là Owner.
     */
    private static final Map<Key, Boolean> POLICY = Map.of(
            new Key(PermissionCodes.JOB_EDIT, "RECRUITER"), true,
            new Key(PermissionCodes.JOB_APPROVE, "HIRING_MANAGER"), false,
            new Key(PermissionCodes.APPLICATION_MOVE_STAGE, "RECRUITER"), true,
            new Key(PermissionCodes.APPLICATION_REJECT, "RECRUITER"), true,
            new Key(PermissionCodes.SCORECARD_SUBMIT, "INTERVIEWER"), true,
            new Key(PermissionCodes.SCORECARD_SUBMIT, "HIRING_MANAGER"), true
    );

    /**
     * Kiểm tra xem người dùng có bị bắt buộc kiểm tra Ownership hay không.
     *
     * QUY TẮC BẮT BỎ:
     * - Nếu TẤT CẢ các Role mà user đang có (được cấp quyền permissionCode này) ĐỀU yêu cầu Ownership -> Trả về TRUE.
     * - Nếu có ÍT NHẤT 1 Role cấp quyền này mà KHÔNG yêu cầu Ownership (ví dụ: Hiring Manager) -> Trả về FALSE
     *   (Quyền rộng hơn của Role đó sẽ thắng).
     * - Mặc định nếu cặp (Permission, Role) không khai báo trong bảng -> KHÔNG yêu cầu Ownership.
     */
    public boolean requiresOwnership(String permissionCode, Set<String> grantingRoleCodes) {
        if (grantingRoleCodes.isEmpty()) {
            return false;
        }
        for (String roleCode : grantingRoleCodes) {
            Boolean required = POLICY.get(new Key(permissionCode, roleCode));
            if (required == null || !required) {
                return false; // Thắng ngay nếu tìm thấy 1 role không cần ownership
            }
        }
        return true;
    }
}
