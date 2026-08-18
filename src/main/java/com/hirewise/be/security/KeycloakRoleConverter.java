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
 * Reads the current user's roles from the Keycloak-issued access token and
 * converts them into {@link GrantedAuthority} instances for Spring Security
 * to use in {@code hasRole()}/{@code hasAuthority()}/{@code @PreAuthorize}.
 * <p>
 * Keycloak stores roles in two places in the JWT:
 * <ul>
 *   <li>{@code realm_access.roles} - realm roles (apply system-wide)</li>
 *   <li>{@code resource_access.<clientId>.roles} - client roles (apply only to one client)</li>
 * </ul>
 * This converter merges both, adds the {@code "ROLE_"} prefix (Spring
 * Security's convention) and uppercases the role name so it can be used
 * with {@code hasRole("ADMIN")} instead of {@code hasAuthority("ROLE_ADMIN")}.
 */
@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Keycloak client id whose client-scoped roles are read from
     * {@code resource_access.<clientId>.roles}. Leave blank
     * ({@code app.keycloak.resource-client-id=}) if only realm roles are used.
     */
    private final String resourceClientId;

    public KeycloakRoleConverter(
            @Value("${app.keycloak.resource-client-id:}") String resourceClientId) {
        this.resourceClientId = resourceClientId;
    }

    /**
     * @param jwt the validated access token issued by Keycloak
     * @return the merged set of realm + client authorities, each prefixed
     *         with {@code "ROLE_"} and uppercased
     */
    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. realm_access.roles - apply across the whole realm
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> realmRoles) {
            realmRoles.forEach(role -> authorities.add(toAuthority((String) role)));
        }

        // 2. resource_access.<clientId>.roles - only merged in when a resource
        // client id has been configured (client-scoped roles are optional).
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
