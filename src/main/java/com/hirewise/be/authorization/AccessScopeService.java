package com.hirewise.be.authorization;

import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * RBAC layer 3 (Access Scope). Cai dat dung theo pseudocode
 * isWithinAccessScope o muc 3.3 cua RBAC Design: UNION toan bo scope dang
 * hieu luc cua user (BR-RBAC-05), ke thua phong ban con neu
 * includeSubDepartments=true (BR-RBAC-06, tinh bang recursive CTE - xem
 * DepartmentRepository), va bat buoc can_write=true cho hanh dong ghi
 * (BR-RBAC-02).
 */


/**
 * Kiểm tra hành động có nằm trong phạm vi (Scope) cho phép của người dùng không.
 * Layer 3 (Access Scope).
 * Xác định người dùng có phạm vi quyền hạn (System, Department, Job) trên dữ liệu mục tiêu hay không.
 */
@Component
public class AccessScopeService {

    private final UserAccessScopeRepository scopeRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;

    public AccessScopeService(UserAccessScopeRepository scopeRepository,
                               DepartmentRepository departmentRepository,
                               Clock clock) {
        this.scopeRepository = scopeRepository;
        this.departmentRepository = departmentRepository;
        this.clock = clock;
    }

    /**
     * Kiểm tra hành động có nằm trong phạm vi (Scope) cho phép của người dùng không.
     */
    public boolean isWithinScope(Long userId, ResourceContext resource, boolean requiresWrite) {
        // Nếu không có context tài nguyên, không gắn với 1 deparment/job cụ thể (hành động dùng chung như USER_CREATE, ROLE_ASSIGN)  thì layer 2 check permission là đủ rồi, bỏ qua check resource scope
        if (resource == null) {
            return true;
        }

        Instant now = Instant.now(clock);
        // Lấy tất cả phạm vi quyền hạn đang còn hiệu lực của user
        List<UserAccessScope> scopes = scopeRepository.findActiveScopes(userId, now);

        for (UserAccessScope scope : scopes) {
            // Nếu hành động yêu cầu quyền Ghi (requiresWrite = true) nhưng Scope chỉ cho phép Đọc -> Bỏ qua Scope này
            if (requiresWrite && !scope.isCanWrite()) {
                continue;
            }
            // Kiểm tra theo loại Scope
            switch (scope.getScopeType()) {
                case SYSTEM -> {
                    // Quyền toàn hệ thống -> Cho phép tất cả
                    return true;
                }
                case JOB -> {
                    // Quyền theo từng Công việc cụ thể -> Khớp jobId
                    if (resource.jobId() != null && resource.jobId().equals(scope.getJobId())) {
                        return true;
                    }
                }
                case DEPARTMENT -> {
                    // Quyền theo Phòng ban -> Kiểm tra ID phòng ban và cây phòng ban con
                    if (isDepartmentWithinScope(scope, resource.departmentId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Kiểm tra phòng ban mục tiêu có nằm trong phạm vi quản lý của Scope không.
     */
    private boolean isDepartmentWithinScope(UserAccessScope scope, Long targetDepartmentId) {
        if (targetDepartmentId == null || scope.getDepartment() == null) {
            return false;
        }
        Long scopeDepartmentId = scope.getDepartment().getId();
        // Nếu không bao gồm phòng ban con -> Chỉ so sánh chính xác ID phòng ban
        if (!scope.isIncludeSubDepartments()) {
            return scopeDepartmentId.equals(targetDepartmentId);
        }
        // Nếu bao gồm phòng ban con -> Đệ quy lấy tất cả ID phòng ban con cháu để kiểm tra
        List<Long> allowedDepartmentIds = departmentRepository.findSelfAndDescendantIds(scopeDepartmentId);
        return allowedDepartmentIds.contains(targetDepartmentId);
    }
}
