package com.hirewise.be.security;

import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.exception.AccountNotActiveException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Optional;

/**
 * RBAC layer 1 (Authentication Freshness, BR-AUTH-07). Runs right after
 * the JWT has been validated for signature/expiry (BearerTokenAuthenticationFilter),
 * looks up the corresponding {@link UserSnapshot} (short-TTL cache - see
 * {@link UserDirectoryService}) and rejects the request if the account has
 * not been provisioned in our system or is no longer ACTIVE - even if the
 * token itself was just issued by Keycloak and is technically still valid.
 * <p>
 * Deliberately NOT registered as a {@code @Component} (to avoid Spring Boot
 * auto-applying it to EVERY other filter chain) - it is constructed manually
 * and wired into the filter chain in {@code SecurityConfig}, the same way
 * as {@link UserContextMdcFilter}.
 * <p>
 * Shares the same "delegate to {@link HandlerExceptionResolver}" pattern as
 * {@link CustomAccessDeniedHandler}/{@link CustomAuthenticationEntryPoint}:
 * an exception raised inside a filter (before {@code DispatcherServlet}) is
 * never caught automatically by {@code @RestControllerAdvice}, so we must
 * explicitly invoke the resolver to get the same unified 403 JSON format
 * used everywhere else.
 */
public class AuthenticationFreshnessFilter extends OncePerRequestFilter {

    private final UserDirectoryService userDirectoryService;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthenticationFreshnessFilter(UserDirectoryService userDirectoryService,
                                          HandlerExceptionResolver exceptionResolver) {
        this.userDirectoryService = userDirectoryService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
            String keycloakId = jwtAuth.getToken().getSubject();
            Optional<UserSnapshot> snapshot = userDirectoryService.lookup(keycloakId);

            // Reject if the account was never provisioned locally, or has been
            // blocked/disabled since the JWT was issued - a valid signature alone
            // is not enough per BR-AUTH-07.
            if (snapshot.isEmpty() || snapshot.get().status() != UserStatus.ACTIVE) {
                // Delegate to the same exception resolver used by @RestControllerAdvice
                // so the response body matches every other error in the app.
                exceptionResolver.resolveException(request, response, null, new AccountNotActiveException());
                return; // Stop here - do not let the request reach the controller/service layer.
            }

            request.setAttribute(UserSnapshot.REQUEST_ATTRIBUTE, snapshot.get());
        }
        // No JWT on this request (permitAll endpoint, or it would already have
        // been rejected with 401 upstream) - nothing to check, continue as normal.
        filterChain.doFilter(request, response);
    }
}
