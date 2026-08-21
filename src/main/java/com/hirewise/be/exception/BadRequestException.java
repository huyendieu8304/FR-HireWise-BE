package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * Generic 400 Bad Request for business validation failures that don't fit
 * a more specific exception type (e.g. an invalid combination of fields,
 * or input that is semantically invalid even though it passed bean
 * validation).
 */
public class BadRequestException extends BaseException {
    public BadRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.BAD_REQUEST, args);
    }
}
