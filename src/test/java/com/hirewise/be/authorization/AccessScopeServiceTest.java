package com.hirewise.be.authorization;

import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Access Scope
 */
@ExtendWith(MockitoExtension.class)
class AccessScopeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
    private static final Long USER_ID = 1L;

    @Mock
    private UserAccessScopeRepository scopeRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private AccessScopeService accessScopeService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        accessScopeService = new AccessScopeService(scopeRepository, departmentRepository, fixedClock);
    }

    private UserAccessScope departmentScope(Long departmentId, boolean includeSub, boolean canWrite) {
        Department department = Department.builder().id(departmentId).build();
        return UserAccessScope.builder()
                .user(User.builder().id(USER_ID).build())
                .scopeType(ScopeType.DEPARTMENT)
                .department(department)
                .includeSubDepartments(includeSub)
                .canWrite(canWrite)
                .validFrom(NOW.minusSeconds(3600))
                .build();
    }

    @Test
    void unionsMultipleDepartmentScopes_BR_RBAC_05() {
        // User co scope tren ca phong IT (10) lan Sales (20) cung luc.
        when(scopeRepository.findActiveScopes(eq(USER_ID), any())).thenReturn(List.of(
                departmentScope(10L, false, false),
                departmentScope(20L, false, false)
        ));

        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(20L), false)).isTrue();
        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(30L), false)).isFalse();
    }

    @Test
    void includeSubDepartmentsTrue_inheritsDescendants_BR_RBAC_06() {
        when(scopeRepository.findActiveScopes(eq(USER_ID), any())).thenReturn(List.of(
                departmentScope(10L, true, false)
        ));
        // Phong 10 co con la phong 11 (tinh boi recursive CTE that su trong DB).
        when(departmentRepository.findSelfAndDescendantIds(10L)).thenReturn(List.of(10L, 11L));

        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(11L), false)).isTrue();
    }

    @Test
    void includeSubDepartmentsFalse_doesNotInheritDescendants_BR_RBAC_06() {
        when(scopeRepository.findActiveScopes(eq(USER_ID), any())).thenReturn(List.of(
                departmentScope(10L, false, false)
        ));

        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(11L), false)).isFalse();
        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(10L), false)).isTrue();
    }

    @Test
    void writeActionRequiresCanWriteTrueOnScope_BR_RBAC_02() {
        when(scopeRepository.findActiveScopes(eq(USER_ID), any())).thenReturn(List.of(
                departmentScope(10L, false, false) // chi xem
        ));

        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(10L), false)).isTrue();
        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(10L), true)).isFalse();
    }

    @Test
    void systemScope_alwaysPasses() {
        UserAccessScope systemScope = UserAccessScope.builder()
                .user(User.builder().id(USER_ID).build())
                .scopeType(ScopeType.SYSTEM)
                .canWrite(true)
                .validFrom(NOW.minusSeconds(3600))
                .build();
        when(scopeRepository.findActiveScopes(eq(USER_ID), any())).thenReturn(List.of(systemScope));

        assertThat(accessScopeService.isWithinScope(USER_ID, ResourceContext.department(999L), true)).isTrue();
    }

    @Test
    void nullResourceContext_skipsScopeCheck() {
        assertThat(accessScopeService.isWithinScope(USER_ID, null, true)).isTrue();
    }
}
