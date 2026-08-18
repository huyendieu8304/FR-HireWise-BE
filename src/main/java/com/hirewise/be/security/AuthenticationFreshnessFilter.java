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
 * RBAC layer 1 (Authentication Freshness, BR-AUTH-07). Chay ngay sau khi
 * JWT da duoc xac thuc chu ky/han dung (BearerTokenAuthenticationFilter),
 * tra UserSnapshot (co cache TTL ngan - xem UserDirectoryService) va tu
 * choi request neu tai khoan chua duoc cap trong he thong hoac khong con
 * ACTIVE - du token vua duoc Keycloak cap con hieu luc.
 *
 * Khong duoc dang ky la @Component (tranh Spring Boot tu dong ap dung no
 * cho MOI filter chain khac) - duoc khoi tao thu cong va gan vao filter
 * chain trong SecurityConfig, giong UserContextMdcFilter.
 *
 * Dung chung co che uy quyen loi cho HandlerExceptionResolver nhu
 * CustomAccessDeniedHandler/CustomAuthenticationEntryPoint: exception phat
 * sinh trong filter (truoc DispatcherServlet) khong tu duoc @RestControllerAdvice
 * bat, phai chu dong goi resolver de co cung format JSON 403 thong nhat.
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

            //kiem tra trang thai cua tai khoan (ACTIVE hay khong)
            if (snapshot.isEmpty() || snapshot.get().status() != UserStatus.ACTIVE) {
                //allow Spring to find and call suitable exception handler (in this case is in our @RestControllerAdvice)
                exceptionResolver.resolveException(request, response, null, new AccountNotActiveException());
                return; // khong cho di tiep vao controller/service
            }

            request.setAttribute(UserSnapshot.REQUEST_ATTRIBUTE, snapshot.get());
        }
        // Request khong co JWT hop le (permitAll, hoac 401 se bi chan o buoc
        // xac thuc truoc do) - khong co gi de kiem, di tiep binh thuong.
        filterChain.doFilter(request, response);
    }
}
