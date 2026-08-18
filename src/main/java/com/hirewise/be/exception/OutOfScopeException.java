package com.hirewise.be.exception;

/**
 * RBAC layer 3 (Access Scope): the user has the required permission, but
 * the target resource (department/Job) falls outside their assigned
 * scope, or they lack {@code can_write} for a write action.
 * <p>
 * See {@link AccountNotActiveException} for why this hardcodes
 * {@link ErrorCode#FORBIDDEN} instead of a more specific code.
 */
public class OutOfScopeException extends ForbiddenActionException {
    public OutOfScopeException() {
        super(ErrorCode.FORBIDDEN);
    }
}
