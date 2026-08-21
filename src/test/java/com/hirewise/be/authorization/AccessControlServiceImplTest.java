package com.hirewise.be.authorization;

import com.hirewise.be.exception.OutOfScopeException;
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RBAC layer 2 (Role-Permission) + layer 3 (Access Scope), theo dung thu
 * tu cua canAccess(user, action, resource) o muc 6 RBAC Design. */
@ExtendWith(MockitoExtension.class)
class AccessControlServiceImplTest {

    @Mock
    private RolePermissionCache rolePermissionCache;
    @Mock
    private AccessScopeService accessScopeService;

    private AccessControlServiceImpl accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlServiceImpl(rolePermissionCache, accessScopeService);
    }

    private CurrentUser recruiter(Long userId) {
        return new CurrentUser(userId, "r@test.com", "Recruiter One", Set.of("RECRUITER"));
    }

    @Test
    void deniesWhenNoRoleGrantsPermission() {
        when(rolePermissionCache.permissionsOf("RECRUITER")).thenReturn(Map.of());

        assertThatThrownBy(() -> accessControlService.checkAccess(
                recruiter(1L), PermissionCodes.JOB_APPROVE, ResourceContext.department(10L)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void deniesWhenPermissionGrantedButOutOfScope() {
        when(rolePermissionCache.permissionsOf("RECRUITER")).thenReturn(Map.of(PermissionCodes.JOB_CREATE, true));
        when(accessScopeService.isWithinScope(any(), any(), eq(true))).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.checkAccess(
                recruiter(1L), PermissionCodes.JOB_CREATE, ResourceContext.department(999L)))
                .isInstanceOf(OutOfScopeException.class);
    }

    @Test
    void allowsWhenPermissionGrantedAndWithinScope() {
        when(rolePermissionCache.permissionsOf("RECRUITER")).thenReturn(Map.of(PermissionCodes.JOB_CREATE, true));
        when(accessScopeService.isWithinScope(any(), any(), eq(true))).thenReturn(true);

        assertThatCode(() -> accessControlService.checkAccess(
                recruiter(1L), PermissionCodes.JOB_CREATE, ResourceContext.department(10L)))
                .doesNotThrowAnyException();

        verify(accessScopeService).isWithinScope(1L, ResourceContext.department(10L), true);
    }

    @Test
    void readActionDoesNotRequireCanWrite() {
        when(rolePermissionCache.permissionsOf("RECRUITER")).thenReturn(Map.of(PermissionCodes.JOB_VIEW, false));
        when(accessScopeService.isWithinScope(any(), any(), eq(false))).thenReturn(true);

        assertThatCode(() -> accessControlService.checkAccess(
                recruiter(1L), PermissionCodes.JOB_VIEW, ResourceContext.department(10L)))
                .doesNotThrowAnyException();

        verify(accessScopeService).isWithinScope(1L, ResourceContext.department(10L), false);
    }
}
