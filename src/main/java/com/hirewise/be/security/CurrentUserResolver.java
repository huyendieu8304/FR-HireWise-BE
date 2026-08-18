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
 * Lets controllers receive a {@link CurrentUser} directly by declaring a
 * parameter annotated {@code @CurrentUserPrincipal CurrentUser user},
 * instead of manually casting {@code Authentication} -> {@code Jwt} and
 * reading each claim by hand.
 */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final String resourceClientId;

    public CurrentUserResolver(@Value("${app.keycloak.resource-client-id:}") String resourceClientId) {
        this.resourceClientId = resourceClientId;
    }

    /**
     * @return {@code true} only for parameters typed {@link CurrentUser}
     *         and annotated with {@link CurrentUserPrincipal}
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class)
                && parameter.hasParameterAnnotation(CurrentUserPrincipal.class);
    }

    /**
     * @return the resolved {@link CurrentUser}, or {@code null} if the
     *         request principal is not JWT-based (this resolver only
     *         applies to OAuth2 resource server requests)
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // request.getUserPrincipal() returns the Authentication itself (Spring
        // Security wraps the request to make this work), NOT the Jwt directly.
        if (!(webRequest.getUserPrincipal() instanceof JwtAuthenticationToken authenticationToken)) {
            return null;
        }
        Jwt jwt = authenticationToken.getToken();

        // Internal userId was already loaded into the request attribute by
        // AuthenticationFreshnessFilter (RBAC layer 1).
        UserSnapshot snapshot = (UserSnapshot) webRequest.getAttribute(
                UserSnapshot.REQUEST_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);

        return fromJwt(jwt, resourceClientId, snapshot);
    }

    /**
     * Builds a {@link CurrentUser} from raw JWT claims plus the internal
     * {@link UserSnapshot} resolved earlier in the request.
     *
     * @param jwt              the validated access token for this request
     * @param resourceClientId Keycloak client id whose client-level roles
     *                         should also be merged in; if blank, only
     *                         realm roles are used
     * @param snapshot         internal user snapshot (may be {@code null}
     *                         when called outside a full request, e.g. tests)
     * @return a populated {@link CurrentUser}; {@code userId} is {@code null}
     *         when {@code snapshot} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static CurrentUser fromJwt(Jwt jwt, String resourceClientId, UserSnapshot snapshot) {
        Set<String> roles = new HashSet<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> realmRoles) {
            realmRoles.forEach(r -> roles.add(((String) r).toUpperCase()));
        }

        // Client roles are optional - only merged in if a resource client id
        // was configured (some deployments use realm roles exclusively).
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
