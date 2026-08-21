package com.hirewise.be.exception;

/**
 * RBAC layer 2 (Role-Permission): none of the user's roles grant the
 * permission required for this action.
 * <p>
 * See {@link AccountNotActiveException} for why this hardcodes
 * {@link ErrorCode#FORBIDDEN} instead of a more specific code.
 */
public class PermissionDeniedException extends ForbiddenActionException {
    public PermissionDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
