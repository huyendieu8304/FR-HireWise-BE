package com.hirewise.be.repository;

import com.hirewise.be.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    /**
     * Truy vấn danh sách quyền và cờ thao tác Ghi (is_write) theo ma roleCode.
     * Sử dụng Native Query join trực tiếp bảng trung gian role_permissions để tối ưu hiệu năng.
     */
    @Query(value = """
            SELECT p.code AS code, p.is_write AS writeFlag
            FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.permission_id
            JOIN roles r ON r.role_id = rp.role_id
            WHERE r.code = :roleCode
            """, nativeQuery = true)
    List<RolePermissionRow> findByRoleCode(@Param("roleCode") String roleCode);

    /**
     * Projection Interface để hứng kết quả từ Native Query.
     */
    interface RolePermissionRow {
        String getCode();
        boolean isWriteFlag();
    }
}
