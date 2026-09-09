package com.hirewise.be.authorization;

import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BR-RPT-02 cho UC-42/UC-43: bien Access Scope thanh tap Job ma bao cao duoc
 * phep tong hop.
 *
 * <p>Diem khac biet so voi JobService: bao cao con phai cong them cac Job ma
 * nguoi dung dang lam Recruiter, ke ca khi Job do nam ngoai phong ban trong
 * scope - "Recruiter thay Job minh quan ly". Layer 4 (@RequiresOwnership) chi
 * gac duoc 1 resource theo 1 path variable nen khong dien dat duoc dieu nay.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReportScopeResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-09T03:00:00Z");
    private static final CurrentUser USER =
            new CurrentUser(7L, "rec@hirewise.vn", "Recruiter", Set.of("RECRUITER"));

    @Mock
    private UserAccessScopeRepository userAccessScopeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private JobPositionRepository jobPositionRepository;

    private ReportScopeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ReportScopeResolver(userAccessScopeRepository, departmentRepository,
                jobPositionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void systemScope_returnsNullMeaningNoRestriction() {
        when(userAccessScopeRepository.findActiveScopes(7L, NOW))
                .thenReturn(List.of(scope(ScopeType.SYSTEM, null, false, null)));

        List<UUID> visible = resolver.resolveVisibleJobIds(USER);

        assertThat(visible).isNull();
        verifyNoInteractions(jobPositionRepository);
    }

    @Test
    void departmentScopeWithSubDepartments_expandsThroughTheRecursiveQuery() {
        when(userAccessScopeRepository.findActiveScopes(7L, NOW))
                .thenReturn(List.of(scope(ScopeType.DEPARTMENT, department(3L), true, null)));
        when(departmentRepository.findSelfAndDescendantIds(3L)).thenReturn(List.of(3L, 8L, 9L));
        UUID jobId = UUID.randomUUID();
        when(jobPositionRepository.findIdsInDepartmentsOrOwnedBy(List.of(3L, 8L, 9L), 7L))
                .thenReturn(List.of(jobId));

        List<UUID> visible = resolver.resolveVisibleJobIds(USER);

        assertThat(visible).containsExactly(jobId);
    }

    @Test
    void departmentScopeWithoutSubDepartments_staysOnThatOneDepartment() {
        when(userAccessScopeRepository.findActiveScopes(7L, NOW))
                .thenReturn(List.of(scope(ScopeType.DEPARTMENT, department(3L), false, null)));
        when(jobPositionRepository.findIdsInDepartmentsOrOwnedBy(List.of(3L), 7L))
                .thenReturn(List.of());

        resolver.resolveVisibleJobIds(USER);

        verify(departmentRepository, never()).findSelfAndDescendantIds(anyLong());
    }

    @Test
    void noDepartmentScope_stillSeesTheJobsTheUserRecruitsFor() {
        when(userAccessScopeRepository.findActiveScopes(7L, NOW)).thenReturn(List.of());
        UUID ownJob = UUID.randomUUID();
        when(jobPositionRepository.findIdsOwnedBy(7L)).thenReturn(List.of(ownJob));

        List<UUID> visible = resolver.resolveVisibleJobIds(USER);

        assertThat(visible).containsExactly(ownJob);
        verify(jobPositionRepository, never()).findIdsInDepartmentsOrOwnedBy(anyList(), anyLong());
    }

    @Test
    void jobScopeRow_addsThatJobOnTopOfTheDepartmentGrant() {
        UUID lentJob = UUID.randomUUID();
        UUID departmentJob = UUID.randomUUID();
        when(userAccessScopeRepository.findActiveScopes(7L, NOW))
                .thenReturn(List.of(
                        scope(ScopeType.DEPARTMENT, department(3L), false, null),
                        scope(ScopeType.JOB, null, false, lentJob)));
        when(jobPositionRepository.findIdsInDepartmentsOrOwnedBy(List.of(3L), 7L))
                .thenReturn(List.of(departmentJob));

        List<UUID> visible = resolver.resolveVisibleJobIds(USER);

        assertThat(visible).containsExactlyInAnyOrder(departmentJob, lentJob);
    }

    @Test
    void nothingGranted_returnsAnEmptyListNotNull() {
        when(userAccessScopeRepository.findActiveScopes(7L, NOW)).thenReturn(List.of());
        when(jobPositionRepository.findIdsOwnedBy(7L)).thenReturn(List.of());

        List<UUID> visible = resolver.resolveVisibleJobIds(USER);

        // Empty and null mean opposite things here: empty is "sees nothing",
        // null is "sees everything". Getting them the wrong way round would
        // hand a scopeless user the whole company.
        assertThat(visible).isNotNull().isEmpty();
    }

    private static Department department(Long id) {
        Department department = new Department();
        department.setId(id);
        return department;
    }

    private static UserAccessScope scope(ScopeType type, Department department,
                                         boolean includeSubDepartments, UUID jobId) {
        return UserAccessScope.builder()
                .scopeType(type)
                .department(department)
                .includeSubDepartments(includeSubDepartments)
                .jobId(jobId)
                .validFrom(NOW.minusSeconds(3600))
                .build();
    }
}
