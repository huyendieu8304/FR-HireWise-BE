package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * The activation/refresh token supplied by the client is missing, already
 * used, expired, or does not match any known token.
 */
public class InvalidTokenException extends BaseException {
    public InvalidTokenException(ErrorCode errorCode) {
        super(errorCode, HttpStatus.BAD_REQUEST);
    }
}
