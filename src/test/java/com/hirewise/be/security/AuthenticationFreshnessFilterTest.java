package com.hirewise.be.security;

import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.exception.AccountNotActiveException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Authentication Freshness
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFreshnessFilterTest {

    @Mock
    private UserDirectoryService userDirectoryService;
    @Mock
    private SessionRegistryService sessionRegistryService;
    @Mock
    private HandlerExceptionResolver exceptionResolver;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Jwt jwt(String subject, UUID sessionId) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", subject)
                .claim("sid", sessionId != null ? sessionId.toString() : null)
                .build();
    }

    @Test
    void blockedAccountWithStillValidJwtAndActiveSession_isDeniedNotProceeded() throws Exception {
        Jwt jwt = jwt("1", SESSION_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        UserSnapshot blockedSnapshot = new UserSnapshot(1L, UserStatus.BLOCKED);
        when(userDirectoryService.lookup(1L)).thenReturn(Optional.of(blockedSnapshot));
        when(sessionRegistryService.isActive(SESSION_ID)).thenReturn(true);

        AuthenticationFreshnessFilter filter = new AuthenticationFreshnessFilter(userDirectoryService, sessionRegistryService, exceptionResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), any(AccountNotActiveException.class));
        verify(filterChain, never()).doFilter(any(), any());
        org.assertj.core.api.Assertions.assertThat(request.getAttribute(UserSnapshot.class.getName())).isNull();
    }

    @Test
    void unprovisionedAccount_isDenied() throws Exception {
        Jwt jwt = jwt("999", SESSION_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        when(userDirectoryService.lookup(999L)).thenReturn(Optional.empty());

        AuthenticationFreshnessFilter filter = new AuthenticationFreshnessFilter(userDirectoryService, sessionRegistryService, exceptionResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), any(AccountNotActiveException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void activeAccountButRevokedSession_isDenied() throws Exception {
        Jwt jwt = jwt("2", SESSION_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        UserSnapshot activeSnapshot = new UserSnapshot(2L, UserStatus.ACTIVE);
        when(userDirectoryService.lookup(2L)).thenReturn(Optional.of(activeSnapshot));
        // Session was revoked (logout) even though the JWT itself has not expired yet.
        when(sessionRegistryService.isActive(SESSION_ID)).thenReturn(false);

        AuthenticationFreshnessFilter filter = new AuthenticationFreshnessFilter(userDirectoryService, sessionRegistryService, exceptionResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), any(AccountNotActiveException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void activeAccount_proceedsAndAttachesSnapshot() throws Exception {
        Jwt jwt = jwt("2", SESSION_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        UserSnapshot activeSnapshot = new UserSnapshot(2L, UserStatus.ACTIVE);
        when(userDirectoryService.lookup(2L)).thenReturn(Optional.of(activeSnapshot));
        when(sessionRegistryService.isActive(SESSION_ID)).thenReturn(true);

        AuthenticationFreshnessFilter filter = new AuthenticationFreshnessFilter(userDirectoryService, sessionRegistryService, exceptionResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
        org.assertj.core.api.Assertions.assertThat(request.getAttribute(UserSnapshot.class.getName())).isEqualTo(activeSnapshot);
    }
}
