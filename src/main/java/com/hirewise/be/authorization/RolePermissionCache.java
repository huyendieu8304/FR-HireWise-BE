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
 * Cache for RBAC Layer 2 (Role-Permission).
 * Caches the mapping: roleCode -> Map&lt;permissionCode, isWriteFlag&gt;.
 * Reduces the number of DB queries needed on every API call.
 */
@Component
public class RolePermissionCache {

    private final PermissionRepository permissionRepository;
    private final Cache<String, Map<String, Boolean>> cache;

    public RolePermissionCache(PermissionRepository permissionRepository,
                                @Value("${app.rbac.role-permission-cache-ttl-seconds:300}") long ttlSeconds) {
        this.permissionRepository = permissionRepository;
        // Initialize the Caffeine cache with a configurable time-to-live (TTL)
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(100)
                .build();
    }

    /**
     * Returns the permissions granted to a role, from cache.
     * On a cache miss, automatically loads them from the DB via {@link #loadFromDb}.
     *
     * @param roleCode the role code to look up
     * @return map of permissionCode -> isWriteFlag granted to this role
     */
    public Map<String, Boolean> permissionsOf(String roleCode) {
        return cache.get(roleCode, this::loadFromDb);
    }

    /**
     * Quick check for whether a role is granted a given permission.
     *
     * @param roleCode       the role code to check
     * @param permissionCode the permission code to check for
     * @return {@code true} if the role is granted this permission
     */
    public boolean grants(String roleCode, String permissionCode) {
        return permissionsOf(roleCode).containsKey(permissionCode);
    }

    /**
     * Queries the database directly through the repository and converts the
     * result into a map.
     */
    private Map<String, Boolean> loadFromDb(String roleCode) {
        return permissionRepository.findByRoleCode(roleCode).stream()
                .collect(Collectors.toMap(
                        PermissionRepository.RolePermissionRow::getCode,
                        PermissionRepository.RolePermissionRow::isWriteFlag,
                        (a, b) -> a));
    }
}
