package com.hirewise.be.authorization;

import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.User;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Who is the Hiring Manager for this Job" - {@link JobPosition#getHiringManager()}
 * is a dead column (see {@code guides/04-DATABASE_DESIGN.md} section 13), so
 * this is entirely scope-based: any active HIRING_MANAGER whose Access Scope
 * covers the Job's department. Shared by {@code JobService#notifyHiringManagers}
 * (UC-13), {@code JobMapper}'s display of the Job Detail screen, and
 * {@code SlaBreachWorker} (UC-41).
 */
@ExtendWith(MockitoExtension.class)
class HiringManagerResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final Long DEPARTMENT_ID = 5L;

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessScopeService accessScopeService;

    private HiringManagerResolver resolver;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        resolver = new HiringManagerResolver(userRoleRepository, userRepository, accessScopeService, clock);
    }

    private JobPosition jobInDepartment(Long departmentId) {
        Department department = departmentId != null ? Department.builder().id(departmentId).build() : null;
        return JobPosition.builder().id(UUID.randomUUID()).title("Backend Engineer").department(department).build();
    }

    @Test
    void resolveForJob_noActiveHiringManagerAccountAtAll_returnsEmpty_skipsAccessScopeLookup() {
        when(userRoleRepository.findActiveUserIdsByRoleCode("HIRING_MANAGER", NOW)).thenReturn(List.of());

        List<User> result = resolver.resolveForJob(jobInDepartment(DEPARTMENT_ID), true);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findAllById(any());
        verify(accessScopeService, never()).isWithinScope(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void resolveForJob_onlyManagersWithScopeCoveringDepartment_areReturned() {
        User inScope = User.builder().id(1L).fullName("In Scope").build();
        User outOfScope = User.builder().id(2L).fullName("Out Of Scope").build();
        when(userRoleRepository.findActiveUserIdsByRoleCode("HIRING_MANAGER", NOW)).thenReturn(List.of(1L, 2L));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(inScope, outOfScope));
        when(accessScopeService.isWithinScope(eq(1L), eq(ResourceContext.department(DEPARTMENT_ID)), eq(true)))
                .thenReturn(true);
        when(accessScopeService.isWithinScope(eq(2L), eq(ResourceContext.department(DEPARTMENT_ID)), eq(true)))
                .thenReturn(false);

        List<User> result = resolver.resolveForJob(jobInDepartment(DEPARTMENT_ID), true);

        assertThat(result).containsExactly(inScope);
    }

    @Test
    void resolveForJob_requiresWriteFalse_passedThroughToAccessScopeCheck() {
        User manager = User.builder().id(1L).fullName("Read Only Viewer").build();
        when(userRoleRepository.findActiveUserIdsByRoleCode("HIRING_MANAGER", NOW)).thenReturn(List.of(1L));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(manager));
        when(accessScopeService.isWithinScope(eq(1L), eq(ResourceContext.department(DEPARTMENT_ID)), eq(false)))
                .thenReturn(true);

        List<User> result = resolver.resolveForJob(jobInDepartment(DEPARTMENT_ID), false);

        assertThat(result).containsExactly(manager);
    }

    @Test
    void resolveForJob_jobWithNoDepartment_checksScopeWithNullDepartmentId() {
        when(userRoleRepository.findActiveUserIdsByRoleCode("HIRING_MANAGER", NOW)).thenReturn(List.of(1L));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(User.builder().id(1L).build()));
        when(accessScopeService.isWithinScope(eq(1L), eq(ResourceContext.department(null)), eq(true)))
                .thenReturn(false);

        List<User> result = resolver.resolveForJob(jobInDepartment(null), true);

        assertThat(result).isEmpty();
    }
}
