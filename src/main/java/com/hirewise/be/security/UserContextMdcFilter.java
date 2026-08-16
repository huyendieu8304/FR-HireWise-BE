package com.hirewise.be.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Gan userId + role cua user hien tai vao MDC ngay sau khi Spring Security
 * xac thuc xong JWT, de MOI dong log phat sinh trong phan con lai cua
 * request (service, exception handler...) tu dong co san 2 field nay -
 * dev khong phai tu tay truyen userId/role vao tung câu log.
 *
 * Duoc dang ky vao filter chain qua SecurityConfig#securityFilterChain
 * bang addFilterAfter(..., BearerTokenAuthenticationFilter.class) de dam
 * bao SecurityContext da co Authentication luc filter nay chay.
 */
public class UserContextMdcFilter extends OncePerRequestFilter {

    private static final String MDC_USER_ID = "userId";
    private static final String MDC_USER_ROLES = "userRoles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            populateMdc();
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_USER_ROLES);
        }
    }

    private void populateMdc() {
        if (SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken jwtAuthenticationToken) {

            MDC.put(MDC_USER_ID, jwtAuthenticationToken.getToken().getSubject());

            String roles = jwtAuthenticationToken.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
            MDC.put(MDC_USER_ROLES, roles);
        }
        // Request khong co JWT hop le (vd endpoint permitAll, hoac 401) ->
        // khong co gi de gan, cac dong log lien quan se hien thi userId/role
        // rong - dieu do la binh thuong va mong doi.
    }
}
