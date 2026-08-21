package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all business exceptions in the application. Carries the
 * {@link ErrorCode}, the HTTP status to respond with, and any message
 * arguments needed to build the localized error message.
 * <p>
 * {@code GlobalExceptionHandler} catches every {@code BaseException} and
 * builds a single, consistent {@code ErrorResponse} from it.
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
