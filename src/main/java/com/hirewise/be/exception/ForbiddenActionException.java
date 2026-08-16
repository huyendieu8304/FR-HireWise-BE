package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends BaseException {
    public ForbiddenActionException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.FORBIDDEN, args);
    }
}
