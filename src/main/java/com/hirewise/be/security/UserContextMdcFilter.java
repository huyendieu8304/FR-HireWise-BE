package com.hirewise.be.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Puts the current user's internal id into MDC right after Spring Security
 * finishes authenticating the access token, so EVERY log line emitted for
 * the rest of the request (services, exception handlers, ...) automatically
 * carries this field - developers don't have to manually pass userId into
 * every log statement.
 * <p>
 * Registered into the filter chain via
 * {@code SecurityConfig#securityFilterChain} using
 * {@code addFilterAfter(..., BearerTokenAuthenticationFilter.class)} to
 * guarantee the {@code SecurityContext} already holds an {@code Authentication}
 * by the time this filter runs.
 */
public class UserContextMdcFilter extends OncePerRequestFilter {

    private static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            populateMdc();
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }

    private void populateMdc() {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            MDC.put(MDC_USER_ID, jwtAuthenticationToken.getToken().getSubject());
        }
        // No valid access token on this request (e.g. a permitAll endpoint, or a 401) ->
        // nothing to populate; related log lines will show an empty userId, which is
        // expected and fine.
    }
}
