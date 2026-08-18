package com.hirewise.be.authorization;

import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * RBAC Layer 3 (Access Scope): determines whether an action on a target
 * resource falls within the user's allowed scope (System, Department, or
 * Job).
 * <p>
 * Implements the {@code isWithinAccessScope} pseudocode from section 3.3 of
 * the RBAC Design: takes the UNION of all currently active scopes assigned
 * to the user (BR-RBAC-05), includes descendant departments when
 * {@code includeSubDepartments=true} (BR-RBAC-06, computed via a recursive
 * CTE - see {@code DepartmentRepository}), and requires {@code can_write=true}
 * for write actions (BR-RBAC-02).
 */
@Component
public class AccessScopeService {

    private final UserAccessScopeRepository scopeRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;

    public AccessScopeService(UserAccessScopeRepository scopeRepository,
                               DepartmentRepository departmentRepository,
                               Clock clock) {
        this.scopeRepository = scopeRepository;
        this.departmentRepository = departmentRepository;
        this.clock = clock;
    }

    /**
     * Checks whether the given action falls within one of the user's active
     * access scopes.
     *
     * @param userId        id of the user whose scopes are checked
     * @param resource      department/job the target resource belongs to;
     *                      {@code null} means the action isn't tied to a
     *                      specific resource, in which case Layer 2 alone is
     *                      sufficient and this method returns {@code true}
     * @param requiresWrite whether the action requires write access, in
     *                      which case only scopes with {@code can_write=true}
     *                      are considered
     * @return {@code true} if at least one active scope covers the resource
     */
    public boolean isWithinScope(Long userId, ResourceContext resource, boolean requiresWrite) {
        // No resource context means the action isn't tied to a specific department/job
        // (e.g. USER_CREATE, ROLE_ASSIGN - system administration actions), so the Layer 2
        // permission check already covers it; skip the resource scope check.
        if (resource == null) {
            return true;
        }

        Instant now = Instant.now(clock);
        // Fetch all of the user's currently active access scopes
        List<UserAccessScope> scopes = scopeRepository.findActiveScopes(userId, now);

        for (UserAccessScope scope : scopes) {
            // A write action (requiresWrite = true) can't be satisfied by a read-only scope -> skip it
            if (requiresWrite && !scope.isCanWrite()) {
                continue;
            }
            // Evaluate scope by type
            switch (scope.getScopeType()) {
                case SYSTEM -> {
                    // System-wide scope -> allow everything
                    return true;
                }
                case JOB -> {
                    // Scoped to a specific job -> match on jobId
                    if (resource.jobId() != null && resource.jobId().equals(scope.getJobId())) {
                        return true;
                    }
                }
                case DEPARTMENT -> {
                    // Scoped to a department -> check the department id, including sub-departments
                    if (isDepartmentWithinScope(scope, resource.departmentId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the target department is within the department(s)
     * managed by this scope.
     *
     * @param scope              the department-type scope being evaluated
     * @param targetDepartmentId id of the department the resource belongs to
     * @return {@code true} if the target department is covered by this scope
     */
    private boolean isDepartmentWithinScope(UserAccessScope scope, Long targetDepartmentId) {
        if (targetDepartmentId == null || scope.getDepartment() == null) {
            return false;
        }
        Long scopeDepartmentId = scope.getDepartment().getId();
        // Scope does not include sub-departments -> only an exact department id match counts
        if (!scope.isIncludeSubDepartments()) {
            return scopeDepartmentId.equals(targetDepartmentId);
        }
        // Scope includes sub-departments -> recursively fetch all descendant department ids to check against
        List<Long> allowedDepartmentIds = departmentRepository.findSelfAndDescendantIds(scopeDepartmentId);
        return allowedDepartmentIds.contains(targetDepartmentId);
    }
}
