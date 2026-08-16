package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedActionException extends BaseException {
    public UnauthorizedActionException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.UNAUTHORIZED, args);
    }
}
