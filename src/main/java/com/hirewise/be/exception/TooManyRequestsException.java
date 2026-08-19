package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/** UC-01 "Other Information": per-IP login rate limit exceeded (see {@code security.LoginRateLimiter}). */
public class TooManyRequestsException extends BaseException {
    public TooManyRequestsException() {
        super(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);
    }
}
