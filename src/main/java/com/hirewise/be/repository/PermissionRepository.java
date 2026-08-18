package com.hirewise.be.repository;

import com.hirewise.be.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Permission} entities.
 */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    /**
     * Returns the permission codes and their write flag (is_write) granted
     * to the given role code.
     *
     * @param roleCode code of the role to look up permissions for
     * @return permission codes and write flags granted to the role
     */
    // Native query joins role_permissions directly to permissions/roles
    // instead of loading the full entity graph, for performance.
    @Query(value = """
            SELECT p.code AS code, p.is_write AS writeFlag
            FROM permissions p
            JOIN role_permissions rp ON rp.permission_id = p.permission_id
            JOIN roles r ON r.role_id = rp.role_id
            WHERE r.code = :roleCode
            """, nativeQuery = true)
    List<RolePermissionRow> findByRoleCode(@Param("roleCode") String roleCode);

    /**
     * Projection interface for mapping the native query result of
     * {@link #findByRoleCode(String)}.
     */
    interface RolePermissionRow {
        String getCode();
        boolean isWriteFlag();
    }
}
