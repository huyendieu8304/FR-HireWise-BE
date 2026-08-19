package com.hirewise.be.authorization;

import java.util.UUID;

/**
 * The scope (department/job) that a specific resource belongs to, used by
 * RBAC Layer 3 (Access Scope). {@code null} means the action isn't tied to a
 * specific department/job (e.g. {@code USER_CREATE}, {@code ROLE_ASSIGN} -
 * system administration actions) - in that case {@link AccessControlService}
 * skips Layer 3 and Layer 2 (role-permission) alone decides the outcome.
 */
public record ResourceContext(Long departmentId, UUID jobId) {

    public static ResourceContext none() {
        return null;
    }

    public static ResourceContext department(Long departmentId) {
        return new ResourceContext(departmentId, null);
    }

    public static ResourceContext job(UUID jobId, Long departmentId) {
        return new ResourceContext(departmentId, jobId);
    }
}
