package com.hirewise.be.authorization;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Ownership policy table (Access Policy Table).
 * <p>
 * Defines the rule: for the same permission, one role may REQUIRE the user
 * to be the resource owner, while another role does NOT (e.g. a Recruiter
 * editing a job must own that job, but a Hiring Manager or Director
 * approving a job does not need to own it).
 */
@Component
public class OwnershipPolicyRegistry {

    /** Lookup key for the policy matrix: (permissionCode, roleCode) pair. */
    private record Key(String permissionCode, String roleCode) {
    }

    /**
     * Static configuration matrix of ownership rules:
     * - Key: (Permission, Role)
     * - Value: {@code true} = ownership is required, {@code false} = ownership is not required.
     */
    private static final Map<Key, Boolean> POLICY = Map.of(
            new Key(PermissionCodes.JOB_EDIT, "RECRUITER"), true,
            new Key(PermissionCodes.JOB_APPROVE, "HIRING_MANAGER"), false,
            new Key(PermissionCodes.APPLICATION_MOVE_STAGE, "RECRUITER"), true,
            new Key(PermissionCodes.APPLICATION_REJECT, "RECRUITER"), true,
            new Key(PermissionCodes.SCORECARD_SUBMIT, "INTERVIEWER"), true,
            new Key(PermissionCodes.SCORECARD_SUBMIT, "HIRING_MANAGER"), true,
            // UC-36/UC-37: only the Recruiter who owns the parent Job may make
            // or send an Offer on it - same rule as APPLICATION_REJECT above.
            new Key(PermissionCodes.OFFER_CREATE, "RECRUITER"), true,
            new Key(PermissionCodes.OFFER_SEND, "RECRUITER"), true
    );

    /**
     * Determines whether the current user must pass the ownership check for
     * this permission.
     * <p>
     * Rule: if EVERY role the user has that grants {@code permissionCode}
     * requires ownership -> returns {@code true}. If AT LEAST ONE granting
     * role does NOT require ownership (e.g. Hiring Manager) -> returns
     * {@code false}, since that role's broader access wins. A
     * (permission, role) pair not declared in the table defaults to NOT
     * requiring ownership.
     *
     * @param permissionCode    the permission being checked
     * @param grantingRoleCodes the user's roles that are granted this permission
     * @return {@code true} if ownership must be verified before allowing the action
     */
    public boolean requiresOwnership(String permissionCode, Set<String> grantingRoleCodes) {
        if (grantingRoleCodes.isEmpty()) {
            return false;
        }
        for (String roleCode : grantingRoleCodes) {
            Boolean required = POLICY.get(new Key(permissionCode, roleCode));
            if (required == null || !required) {
                return false; // Found one role that doesn't require ownership -> it wins immediately
            }
        }
        return true;
    }
}
