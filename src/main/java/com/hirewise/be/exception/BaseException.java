package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * Lớp cha cho toàn bộ exception nghiệp vụ. GlobalExceptionHandler bắt
 * BaseException để build ErrorResponse thống nhất (kèm i18n message).
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;
    private final HttpStatus status;

    protected BaseException(ErrorCode errorCode, HttpStatus status, Object... args) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.status = status;
        this.args = args;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
