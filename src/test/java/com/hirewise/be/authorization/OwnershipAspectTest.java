package com.hirewise.be.authorization;

import com.hirewise.be.exception.NotResourceOwnerException;
import com.hirewise.be.repository.UserAccessScopeRepository; // Import thêm
import com.hirewise.be.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Clock; // Import thêm
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RBAC layer 4 (Ownership) - kem ca layer 2/3 ma OwnershipAspect thuc hien
 * truoc do de dam bao dung thu tu BR-RBAC-08. Bao phu: ownership pass/fail,
 * va truong hop 1 role duoc cap permission MA KHONG can ownership (vd
 * HIRING_MANAGER voi JOB_APPROVE) khong bi chan boi ownership check.
 */
@ExtendWith(MockitoExtension.class)
class OwnershipAspectTest {

    @Mock
    private OwnershipResolverRegistry resolverRegistry;
    @Mock
    private OwnershipPolicyRegistry policyRegistry;
    @Mock
    private RolePermissionCache rolePermissionCache;
    @Mock
    private AccessControlService accessControlService;

    // Thêm 2 Mock mới
    @Mock
    private UserAccessScopeRepository userAccessScopeRepository;
    @Mock
    private Clock clock;

    @Mock
    private OwnershipResolver jobPositionResolver;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;

    private OwnershipAspect aspect;

    static class DummyController {
        public void close(UUID id, CurrentUser currentUser) {
        }
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // Cập nhật Constructor truyền đủ 6 tham số
        aspect = new OwnershipAspect(
                resolverRegistry,
                policyRegistry,
                rolePermissionCache,
                accessControlService,
                userAccessScopeRepository,
                clock
        );

        Method method = DummyController.class.getMethod("close", UUID.class, CurrentUser.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
    }

    private static final UUID JOB_ID = UUID.randomUUID();

    private CurrentUser userWithRoles(Long userId, Set<String> roles) {
        return new CurrentUser(userId, "u@test.com", "U", roles);
    }

    private RequiresOwnership annotation() {
        return new RequiresOwnership() {
            public String resourceType() {
                return "JOB_POSITION";
            }

            public String idParam() {
                return "id";
            }

            public String permission() {
                return PermissionCodes.JOB_EDIT;
            }

            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequiresOwnership.class;
            }
        };
    }

    @Test
    void ownerMatches_proceeds() throws Throwable {
        CurrentUser recruiter = userWithRoles(5L, Set.of("RECRUITER"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{JOB_ID, recruiter});
        when(resolverRegistry.get("JOB_POSITION")).thenReturn(jobPositionResolver);
        when(jobPositionResolver.resolve(JOB_ID)).thenReturn(new OwnedResource(5L, 10L, JOB_ID));
        when(rolePermissionCache.grants("RECRUITER", PermissionCodes.JOB_EDIT)).thenReturn(true);
        when(policyRegistry.requiresOwnership(eq(PermissionCodes.JOB_EDIT), any())).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(null);

        assertThatCode(() -> aspect.enforce(joinPoint, annotation())).doesNotThrowAnyException();

        verify(accessControlService).checkAccess(recruiter, PermissionCodes.JOB_EDIT, new ResourceContext(10L, JOB_ID));
        verify(joinPoint).proceed();
    }

    @Test
    void ownerMismatch_throwsNotResourceOwner() throws Throwable {
        CurrentUser recruiter = userWithRoles(5L, Set.of("RECRUITER"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{JOB_ID, recruiter});
        when(resolverRegistry.get("JOB_POSITION")).thenReturn(jobPositionResolver);
        // Job duoc phu trach boi recruiter khac (userId=99), khong phai nguoi goi (5).
        when(jobPositionResolver.resolve(JOB_ID)).thenReturn(new OwnedResource(99L, 10L, JOB_ID));
        when(rolePermissionCache.grants("RECRUITER", PermissionCodes.JOB_EDIT)).thenReturn(true);
        when(policyRegistry.requiresOwnership(eq(PermissionCodes.JOB_EDIT), any())).thenReturn(true);

        assertThatThrownBy(() -> aspect.enforce(joinPoint, annotation()))
                .isInstanceOf(NotResourceOwnerException.class);

        verify(joinPoint, never()).proceed();
    }

    @Test
    void roleGrantingPermissionWithoutOwnershipRequirement_bypassesOwnershipCheck() throws Throwable {
        // Vd: HIRING_MANAGER giu JOB_APPROVE nhung khong bi rang buoc ownership
        // (RBAC Design muc 4.3) - du "owner" khac nguoi goi, van duoc phep.
        CurrentUser hiringManager = userWithRoles(7L, Set.of("HIRING_MANAGER"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{JOB_ID, hiringManager});
        when(resolverRegistry.get("JOB_POSITION")).thenReturn(jobPositionResolver);
        when(jobPositionResolver.resolve(JOB_ID)).thenReturn(new OwnedResource(99L, 10L, JOB_ID));
        when(rolePermissionCache.grants("HIRING_MANAGER", PermissionCodes.JOB_EDIT)).thenReturn(true);
        when(policyRegistry.requiresOwnership(eq(PermissionCodes.JOB_EDIT), any())).thenReturn(false);
        when(joinPoint.proceed()).thenReturn(null);

        assertThatCode(() -> aspect.enforce(joinPoint, annotation())).doesNotThrowAnyException();
        verify(joinPoint).proceed();
    }
}