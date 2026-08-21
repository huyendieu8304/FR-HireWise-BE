package com.hirewise.be.authorization;

import com.hirewise.be.security.CurrentUser;

/**
 * Central entry point for RBAC Layer 2 (Role-Permission) + Layer 3 (Access
 * Scope), corresponding to the {@code canAccess(user, action, resource)}
 * function in section 6 of the RBAC Design (Layer 1 - Authentication
 * Freshness - is already handled earlier in the filter chain by
 * {@code AuthenticationFreshnessFilter}; Layer 4 - Ownership - is handled
 * separately by {@code OwnershipAspect}/{@code @RequiresOwnership}).
 * <p>
 * Called directly from services, right after (or, for create actions, right
 * at the start once) the target department/job is known - since the
 * resource usually needs to be loaded first to determine which scope it
 * belongs to.
 */
public interface AccessControlService {

    /**
     * Checks whether {@code user} is allowed to perform {@code permissionCode}
     * on {@code resource}, enforcing Layer 2 (Role-Permission) then Layer 3
     * (Access Scope) in order.
     *
     * @param user           the currently authenticated user
     * @param permissionCode the permission being checked, see {@link PermissionCodes}
     * @param resource       the scope (department/job) the target resource belongs
     *                       to; pass {@code null} for actions not tied to a specific
     *                       resource (e.g. {@code USER_CREATE}, {@code ROLE_ASSIGN})
     * @throws com.hirewise.be.exception.PermissionDeniedException if none of the
     *      user's roles are granted {@code permissionCode}
     * @throws com.hirewise.be.exception.OutOfScopeException if the user has the
     *      permission but the resource is outside their access scope (or the
     *      scope lacks {@code can_write} for a write action)
     */
    void checkAccess(CurrentUser user, String permissionCode, ResourceContext resource);
}
