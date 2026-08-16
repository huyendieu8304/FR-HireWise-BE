package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

public class BusinessConflictException extends BaseException {
    public BusinessConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.CONFLICT, args);
    }
}
