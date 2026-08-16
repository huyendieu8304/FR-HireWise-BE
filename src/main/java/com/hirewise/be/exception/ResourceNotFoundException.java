package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.NOT_FOUND, args);
    }
}
