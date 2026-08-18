package com.hirewise.be.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when syncing data to Keycloak fails (e.g. assigning a realm role
 * via the Admin API - see {@code KeycloakAdminClient#assignRealmRole}).
 * <p>
 * Unlike regular business errors (404/409/403, which the app itself
 * decides), this represents a failure of an external dependency (Keycloak
 * not responding, the admin client not configured, or the role not
 * existing on the Keycloak side yet). Maps to 502 Bad Gateway -
 * deliberately not 500 - to distinguish "this app has a bug" from "the
 * upstream Identity Provider failed", so FE/QA don't mistake it for a
 * business bug.
 */
public class KeycloakSyncException extends BaseException {
    public KeycloakSyncException(ErrorCode errorCode, Object... args) {
        super(errorCode, HttpStatus.BAD_GATEWAY, args);
    }
}
