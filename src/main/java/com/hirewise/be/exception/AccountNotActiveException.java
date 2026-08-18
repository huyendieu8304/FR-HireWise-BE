package com.hirewise.be.exception;

/**
 * RBAC layer 1 (Authentication Freshness, BR-AUTH-07): thrown when the JWT
 * is valid but the corresponding {@code users.status} is not ACTIVE at
 * request time, or no internal user record exists yet for this identity
 * (i.e. the account hasn't been provisioned via USER_CREATE by an HR
 * Admin).
 * <p>
 * Maps to HTTP 403 Forbidden with {@link ErrorCode#FORBIDDEN}. All RBAC
 * layers hardcode this same generic error code on purpose: the client only
 * needs to know the request was denied, not which internal RBAC layer
 * rejected it (BR-RBAC-03) - the concrete exception class name is only
 * used internally for audit logging.
 */
public class AccountNotActiveException extends ForbiddenActionException {
    public AccountNotActiveException() {
        super(ErrorCode.FORBIDDEN);
    }
}
