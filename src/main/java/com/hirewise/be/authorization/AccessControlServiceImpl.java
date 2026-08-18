package com.hirewise.be.authorization;

import com.hirewise.be.exception.OutOfScopeException;
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Central service that orchestrates the authorization flow.
 * Runs the check through two layers in order: Layer 2 (Role-Permission)
 * followed by Layer 3 (Access Scope).
 */
@Service
public class AccessControlServiceImpl implements AccessControlService {

    private final RolePermissionCache rolePermissionCache;
    private final AccessScopeService accessScopeService;

    public AccessControlServiceImpl(RolePermissionCache rolePermissionCache, AccessScopeService accessScopeService) {
        this.rolePermissionCache = rolePermissionCache;
        this.accessScopeService = accessScopeService;
    }

    @Override
    public void checkAccess(CurrentUser user, String permissionCode, ResourceContext resource) {
        boolean requiresWrite = false;
        boolean granted = false;

        // Layer 2: Role-Permission.
        // Walk through every role the user has to find one that is granted permissionCode.
        // TODO: double check current user's roles - it looks like some extra Keycloak roles
        // get mixed in alongside the business roles, needs verification.
        for (String roleCode : user.roles()) {
            Map<String, Boolean> permissions = rolePermissionCache.permissionsOf(roleCode);
            if (permissions.containsKey(permissionCode)) {
                granted = true;
                requiresWrite = permissions.get(permissionCode); // Flag telling whether this permission covers a Read or a Write action
                break; // Stop at the first role that grants the permission
            }
        }

        // No role of the user grants this permission -> deny access before even checking scope.
        if (!granted) {
            throw new PermissionDeniedException();
        }

        // Layer 3: Access Scope.
        // Check whether the operation on this resource falls within the user's allowed scope.
        if (!accessScopeService.isWithinScope(user.userId(), resource, requiresWrite)) {
            throw new OutOfScopeException();
        }
    }
}
