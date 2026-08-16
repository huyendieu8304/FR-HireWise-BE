package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends BaseException {
    public BadRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.BAD_REQUEST, args);
    }
}
