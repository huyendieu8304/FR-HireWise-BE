package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * 5 consecutive failed logins within 15 minutes locks the account for 15 minutes.
 * Maps to 423 Locked so clients can distinguish it from a plain wrong-password 401.
 */
public class AccountLockedException extends BaseException {
    public AccountLockedException() {
        super(ErrorCode.ACCOUNT_LOCKED, HttpStatus.LOCKED);
    }
}
