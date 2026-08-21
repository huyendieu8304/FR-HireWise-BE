package com.hirewise.be.exception;

/**
 * the account exists and credentials matched, but it is still {@code INVITED} - the user has not yet set a password via the
 * EM-01 activation link (UC-02). Distinct from {@link AccountNotActiveException} (Blocked/Disabled)
 * so the client can show a more helpful "check your email to activate your account" message instead of a generic denial.
 */
public class AccountNotActivatedException extends ForbiddenActionException {
    public AccountNotActivatedException() {
        super(ErrorCode.ACCOUNT_NOT_ACTIVATED);
    }
}
