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
import java.util.UUID;

/**
 * RBAC layer 1 (Authentication Freshness, BR-AUTH-07). Runs right after
 * the access token has been validated for signature/expiry
 * (BearerTokenAuthenticationFilter), then rejects the request unless BOTH:
 * <ol>
 *   <li>the corresponding {@code users.status = ACTIVE} right now (short-TTL
 *       cache - see {@link UserDirectoryService}) - covers BR-AUTH-04
 *       (account blocked/disabled), and</li>
 *   <li>the session referenced by the token's {@code sid} claim has not been
 *       revoked (logout) or expired - see {@link SessionRegistryService}.</li>
 * </ol>
 * Even if the JWT itself is technically still valid (signature ok, not yet
 * expired), either check failing is enough to deny the request.
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
    private final SessionRegistryService sessionRegistryService;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthenticationFreshnessFilter(UserDirectoryService userDirectoryService,
                                          SessionRegistryService sessionRegistryService,
                                          HandlerExceptionResolver exceptionResolver) {
        this.userDirectoryService = userDirectoryService;
        this.sessionRegistryService = sessionRegistryService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
            Long userId = parseUserId(jwtAuth.getToken().getSubject());
            UUID sessionId = parseSessionId(jwtAuth.getToken().getClaimAsString("sid"));

            Optional<UserSnapshot> snapshot = userId != null ? userDirectoryService.lookup(userId) : Optional.empty();
            boolean accountActive = snapshot.isPresent() && snapshot.get().status() == UserStatus.ACTIVE;
            boolean sessionActive = sessionRegistryService.isActive(sessionId);

            // Reject if the account was never provisioned locally, has been blocked/disabled
            // since the token was issued (BR-AUTH-04), or this specific session has been
            // logged out / expired (BR-AUTH-03) - a valid signature alone is not enough per
            // BR-AUTH-07. Both failure reasons deliberately surface the SAME generic error so
            // the client can't distinguish "blocked" from "logged out elsewhere".
            if (!accountActive || !sessionActive) {
                // Delegate to the same exception resolver used by @RestControllerAdvice
                // so the response body matches every other error in the app.
                exceptionResolver.resolveException(request, response, null, new AccountNotActiveException());
                return; // Stop here - do not let the request reach the controller/service layer.
            }

            request.setAttribute(UserSnapshot.class.getName(), snapshot.get());
        }
        // No access token on this request (permitAll endpoint, or it would already have
        // been rejected with 401 upstream) - nothing to check, continue as normal.
        filterChain.doFilter(request, response);
    }

    private Long parseUserId(String subject) {
        try {
            return subject != null ? Long.valueOf(subject) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UUID parseSessionId(String sid) {
        try {
            return sid != null ? UUID.fromString(sid) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
