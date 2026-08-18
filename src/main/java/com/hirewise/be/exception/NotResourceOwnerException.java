package com.hirewise.be.exception;

/**
 * RBAC layer 4 (Ownership): permission and scope checks both passed, but
 * the user is not the designated owner of this specific resource (e.g.
 * not the {@code recruiter_id} of the Job being edited).
 * <p>
 * See {@link AccountNotActiveException} for why this hardcodes
 * {@link ErrorCode#FORBIDDEN} instead of a more specific code.
 */
public class NotResourceOwnerException extends ForbiddenActionException {
    public NotResourceOwnerException() {
        super(ErrorCode.FORBIDDEN);
    }
}
