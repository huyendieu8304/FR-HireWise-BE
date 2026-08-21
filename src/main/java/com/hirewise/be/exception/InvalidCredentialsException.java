package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 *  wrong email/password, or the email has no LOCAL
 * {@code auth_identities} row at all. Deliberately maps to the SAME message
 * for "email not found" and "wrong password" so the response never reveals
 * whether an email is registered (standard login-enumeration defense).
 */
public class InvalidCredentialsException extends BaseException {
    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }
}
