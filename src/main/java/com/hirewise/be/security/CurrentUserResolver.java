package com.hirewise.be.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

/**
 * Lets controllers receive a {@link CurrentUser} directly by declaring a
 * parameter annotated {@code @CurrentUserPrincipal CurrentUser user},
 * instead of manually casting {@code Authentication} -> {@code Jwt} and
 * reading each claim by hand.
 */
@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final ActiveRolesService activeRolesService;

    public CurrentUserResolver(ActiveRolesService activeRolesService) {
        this.activeRolesService = activeRolesService;
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
     *         request principal is not access-token-based (this resolver
     *         only applies to authenticated API requests)
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (!(webRequest.getUserPrincipal() instanceof JwtAuthenticationToken authenticationToken)) {
            return null;
        }
        Jwt jwt = authenticationToken.getToken();
        return fromJwt(jwt, activeRolesService);
    }

    /**
     * Builds a {@link CurrentUser} from our own access token's claims plus
     * a fresh (short-TTL cached) read of the user's active roles.
     *
     * @param jwt               the validated access token for this request
     * @param activeRolesService resolves the caller's currently-active roles from the DB
     * @return a populated {@link CurrentUser}
     */
    public static CurrentUser fromJwt(Jwt jwt, ActiveRolesService activeRolesService) {
        Long userId = Long.valueOf(jwt.getSubject());
        Set<String> roles = activeRolesService.rolesOf(userId);
        return new CurrentUser(
                userId,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                roles
        );
    }
}
