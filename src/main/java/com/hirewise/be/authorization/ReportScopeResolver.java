package com.hirewise.be.authorization;

import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BR-RPT-02 for the two Reporting dashboards (UC-42, UC-43): turns the
 * caller's Access Scope rows into the concrete set of Job Positions their
 * report may aggregate over.
 * <p>
 * Every other list endpoint filters by department alone
 * ({@code JobService#listJobs}), and RBAC layer 4
 * ({@code @RequiresOwnership}) only guards a single resource named by one
 * path variable - neither can express "Recruiter thay Job minh quan ly"
 * across a whole report. So the job-id set is resolved once here and every
 * report query is restricted to it.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ReportScopeResolver {

    UserAccessScopeRepository userAccessScopeRepository;
    DepartmentRepository departmentRepository;
    JobPositionRepository jobPositionRepository;
    Clock clock;

    /**
     * Resolves which Job Positions {@code currentUser} may see in a report.
     *
     * @param currentUser the authenticated caller
     * @return {@code null} when the caller holds a SYSTEM scope and no
     *         restriction applies at all (HR Admin - "thay toan he thong");
     *         otherwise the exact job ids they may aggregate, possibly empty
     *         (caller sees nothing, the report renders ME-37)
     */
    public List<UUID> resolveVisibleJobIds(CurrentUser currentUser) {
        Instant now = Instant.now(clock);
        List<UserAccessScope> activeScopes =
                userAccessScopeRepository.findActiveScopes(currentUser.userId(), now);

        boolean hasSystemScope = activeScopes.stream()
                .anyMatch(scope -> scope.getScopeType() == ScopeType.SYSTEM);
        if (hasSystemScope) {
            return null;
        }

        List<Long> allowedDepartmentIds = resolveDepartmentIds(activeScopes);

        // LinkedHashSet: the three grants below overlap often (a Recruiter
        // usually also has a department scope) and the report queries take the
        // list as an IN (:jobIds) parameter.
        Set<UUID> visible = new LinkedHashSet<>();
        if (allowedDepartmentIds.isEmpty()) {
            visible.addAll(jobPositionRepository.findIdsOwnedBy(currentUser.userId()));
        } else {
            visible.addAll(jobPositionRepository.findIdsInDepartmentsOrOwnedBy(
                    allowedDepartmentIds, currentUser.userId()));
        }

        // A JOB-typed scope row names one job outright (an Interviewer or a
        // stand-in Recruiter lent access to a single Job).
        activeScopes.stream()
                .filter(scope -> scope.getScopeType() == ScopeType.JOB && scope.getJobId() != null)
                .forEach(scope -> visible.add(scope.getJobId()));

        if (visible.isEmpty()) {
            log.debug("Bao cao: user {} khong co Job nao trong Access Scope", currentUser.userId());
        }
        return new ArrayList<>(visible);
    }

    /**
     * Flattens DEPARTMENT scope rows into department ids, expanding to
     * descendants when the row says so (BR-RBAC-06). Mirrors
     * {@code JobService#resolveDepartmentIds}, kept separate on purpose so a
     * change to reporting scope cannot silently alter the Job list.
     *
     * @param activeScopes the caller's currently valid scope rows
     * @return department ids in scope, empty when none are DEPARTMENT-typed
     */
    private List<Long> resolveDepartmentIds(List<UserAccessScope> activeScopes) {
        List<Long> result = new ArrayList<>();
        for (UserAccessScope scope : activeScopes) {
            if (scope.getScopeType() != ScopeType.DEPARTMENT || scope.getDepartment() == null) {
                continue;
            }
            Long rootId = scope.getDepartment().getId();
            if (scope.isIncludeSubDepartments()) {
                result.addAll(departmentRepository.findSelfAndDescendantIds(rootId));
            } else {
                result.add(rootId);
            }
        }
        return result;
    }
}
