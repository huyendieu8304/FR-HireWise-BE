package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * 403 Forbidden: the authenticated user is not allowed to perform the
 * requested action. Base class for the RBAC-layer-specific exceptions
 * ({@link AccountNotActiveException}, {@link PermissionDeniedException},
 * {@link OutOfScopeException}, {@link NotResourceOwnerException}); can
 * also be thrown directly for other authorization failures outside the
 * RBAC pipeline.
 */
public class ForbiddenActionException extends BaseException {
    public ForbiddenActionException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.FORBIDDEN, args);
    }
}
