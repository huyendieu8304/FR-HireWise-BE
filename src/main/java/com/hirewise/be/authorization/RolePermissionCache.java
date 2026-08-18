package com.hirewise.be.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.repository.PermissionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quản lý Cache cho RBAC Layer 2 (Role-Permission).
 * Cache ánh xạ: roleCode -> Map<permissionCode, isWriteFlag>.
 * Giúp giảm tải truy vấn DB liên tục khi gọi API.
 */
@Component
public class RolePermissionCache {

    private final PermissionRepository permissionRepository;
    private final Cache<String, Map<String, Boolean>> cache;

    public RolePermissionCache(PermissionRepository permissionRepository,
                                @Value("${app.rbac.role-permission-cache-ttl-seconds:300}") long ttlSeconds) {
        this.permissionRepository = permissionRepository;
        // Khởi tạo Caffeine Cache với thời gian hết hạn (TTL) configurable
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(100)
                .build();
    }

    /**
     * Lấy danh sách permission của 1 role từ cache.
     * Nếu chưa có trong cache (Cache Miss), tự động gọi loadFromDb để nạp vào cache.
     */
    //todo cho nay, current user co them nhung role khac cua keycloak bo vao ngoai nhung business role, check lai
    public Map<String, Boolean> permissionsOf(String roleCode) {
        return cache.get(roleCode, this::loadFromDb);
    }

    /**
     * Helper check nhanh xem 1 role có chứa permissionCode hay không.
     */
    public boolean grants(String roleCode, String permissionCode) {
        return permissionsOf(roleCode).containsKey(permissionCode);
    }

    /**
     * Truy vấn trực tiếp Database thông qua Repository và convert kết quả thành Map.
     */
    private Map<String, Boolean> loadFromDb(String roleCode) {
        return permissionRepository.findByRoleCode(roleCode).stream()
                .collect(Collectors.toMap(
                        PermissionRepository.RolePermissionRow::getCode,
                        PermissionRepository.RolePermissionRow::isWriteFlag,
                        (a, b) -> a));
    }
}
