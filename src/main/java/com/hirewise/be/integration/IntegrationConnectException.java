package com.hirewise.be.integration;

/**
 * Thrown when talking to a Cloud Storage provider's OAuth endpoint fails
 * (network error, non-2xx response, malformed body). Caught by
 * {@code CloudStorageIntegrationService.handleCallback} and turned into a
 * failed-connect redirect (UC-07 EX-01) rather than surfaced as a JSON
 * error - the caller at that point is the provider's redirect, not an API
 * client that could read a JSON body.
 */
public class IntegrationConnectException extends RuntimeException {
    public IntegrationConnectException(String message, Throwable cause) {
        super(message, cause);
    }

    public IntegrationConnectException(String message) {
        super(message);
    }
}
