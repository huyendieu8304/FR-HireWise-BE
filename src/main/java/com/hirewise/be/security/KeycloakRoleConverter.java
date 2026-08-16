package com.hirewise.be.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đọc role của user từ access token do Keycloak phát hành và chuyển
 * thành {@link GrantedAuthority} cho Spring Security dùng trong
 * hasRole()/hasAuthority()/@PreAuthorize.
 *
 * Keycloak để role ở 2 chỗ trong JWT:
 *  - "realm_access.roles"                -> realm role (áp dụng toàn hệ thống)
 *  - "resource_access.<clientId>.roles"  -> client role (chỉ áp dụng cho 1 client)
 *
 * Converter này gộp cả 2, thêm tiền tố "ROLE_" (chuẩn của Spring Security)
 * và uppercase để dùng được với hasRole("ADMIN") thay vì phải gọi
 * hasAuthority("ROLE_ADMIN").
 */
@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * clientId của client (resource) trong Keycloak mà bạn gán client role
     * cho user, dùng để đọc resource_access.<clientId>.roles.
     * Nếu bạn chỉ dùng realm roles, có thể để trống (app.keycloak.resource-client-id=)
     */
    private final String resourceClientId;

    public KeycloakRoleConverter(
            @Value("${app.keycloak.resource-client-id:}") String resourceClientId) {
        this.resourceClientId = resourceClientId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> realmRoles) {
            realmRoles.forEach(role -> authorities.add(toAuthority((String) role)));
        }

        // 2. resource_access.<clientId>.roles
        if (resourceClientId != null && !resourceClientId.isBlank()) {
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null && resourceAccess.get(resourceClientId) instanceof Map<?, ?> clientAccess) {
                Object rolesObj = clientAccess.get("roles");
                if (rolesObj instanceof List<?> clientRoles) {
                    clientRoles.forEach(role -> authorities.add(toAuthority((String) role)));
                }
            }
        }

        return authorities;
    }

    private GrantedAuthority toAuthority(String role) {
        return new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase());
    }
}
