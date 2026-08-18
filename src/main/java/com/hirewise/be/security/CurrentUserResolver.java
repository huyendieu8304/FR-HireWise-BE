package com.hirewise.be.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cho phép controller nhận thẳng {@link CurrentUser} bằng cách khai báo
 * tham số {@code @CurrentUserPrincipal CurrentUser user} thay vì phải tự
 * ép kiểu {@code Authentication} -> {@code Jwt} -> đọc từng claim.
 */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final String resourceClientId;

    public CurrentUserResolver(@Value("${app.keycloak.resource-client-id:}") String resourceClientId) {
        this.resourceClientId = resourceClientId;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class)
                && parameter.hasParameterAnnotation(CurrentUserPrincipal.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // request.getUserPrincipal() trả về chính Authentication (Spring Security wrap request để làm điều này), KHÔNG phải trực tiếp Jwt.
        if (!(webRequest.getUserPrincipal() instanceof JwtAuthenticationToken authenticationToken)) {
            return null;
        }
        Jwt jwt = authenticationToken.getToken();

        // userId nội bộ đã được AuthenticationFreshnessFilter (RBAC layer 1) nạp sẵn vào request attribute
        UserSnapshot snapshot = (UserSnapshot) webRequest.getAttribute(
                UserSnapshot.REQUEST_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);

        return fromJwt(jwt, resourceClientId, snapshot);
    }

    @SuppressWarnings("unchecked")
    public static CurrentUser fromJwt(Jwt jwt, String resourceClientId, UserSnapshot snapshot) {
        Set<String> roles = new HashSet<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> realmRoles) {
            realmRoles.forEach(r -> roles.add(((String) r).toUpperCase()));
        }

        if (resourceClientId != null && !resourceClientId.isBlank()) {
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null && resourceAccess.get(resourceClientId) instanceof Map<?, ?> clientAccess
                    && clientAccess.get("roles") instanceof List<?> clientRoles) {
                clientRoles.forEach(r -> roles.add(((String) r).toUpperCase()));
            }
        }

        return new CurrentUser(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                roles,
                snapshot != null ? snapshot.userId() : null
        );
    }
}
