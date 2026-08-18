package com.hirewise.be.authorization;

import com.hirewise.be.exception.OutOfScopeException;
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service trung tâm điều phối quá trình phân quyền (Authorization).
 * Phối hợp kiểm tra qua 2 lớp: Layer 2 (Role-Permission) và Layer 3 (Access Scope).
 */
@Service
public class AccessControlServiceImpl implements AccessControlService {

    private final RolePermissionCache rolePermissionCache;
    private final AccessScopeService accessScopeService;

    public AccessControlServiceImpl(RolePermissionCache rolePermissionCache, AccessScopeService accessScopeService) {
        this.rolePermissionCache = rolePermissionCache;
        this.accessScopeService = accessScopeService;
    }

    @Override
    public void checkAccess(CurrentUser user, String permissionCode, ResourceContext resource) {
        boolean requiresWrite = false;
        boolean granted = false;

        // 1 Role-Permission
        // Duyệt qua tất cả các role của user xem có role nào chứa permissionCode yêu cầu không
        //todo check lai roles cua current user, hinh nhu co chua may cai role khac cua keycloak nua
        for (String roleCode : user.roles()) {
            Map<String, Boolean> permissions = rolePermissionCache.permissionsOf(roleCode);
            if (permissions.containsKey(permissionCode)) {
                granted = true;
                requiresWrite = permissions.get(permissionCode); // Lấy cờ xác định đây là thao tác Đọc hay Ghi
                break; // Tìm thấy quyền phù hợp thì dừng vòng lặp
            }
        }

        //user khong thuoc role nao chua permission code -> access denied
        if (!granted) {
            throw new PermissionDeniedException();
        }

        // 2 Access Scope
        // Kiểm tra xem thao tác trên tài nguyên (resource) có nằm trong phạm vi cho phép của user không
        if (!accessScopeService.isWithinScope(user.userId(), resource, requiresWrite)) {
            throw new OutOfScopeException();
        }
    }
}
