package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * 401 Unauthorized: the request lacks valid authentication credentials.
 */
public class UnauthorizedActionException extends BaseException {
    public UnauthorizedActionException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.UNAUTHORIZED, args);
    }
}
