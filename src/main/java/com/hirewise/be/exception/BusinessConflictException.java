package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 Conflict: the request is valid but conflicts with the current state
 * of the resource (e.g. a duplicate unique field, or trying to close an
 * already-closed job posting).
 */
public class BusinessConflictException extends BaseException {
    public BusinessConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.CONFLICT, args);
    }
}
