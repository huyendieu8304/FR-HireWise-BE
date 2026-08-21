package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * 404 Not Found: the requested resource does not exist (e.g. looked up by
 * id and no matching record was found).
 */
public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.NOT_FOUND, args);
    }
}
