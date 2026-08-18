package com.hirewise.be.exception;

/**
 * Business error codes used across the system. Each value maps to a
 * message key in {@code messages.properties} to support localized (i18n)
 * messages returned to the client.
 */
public enum ErrorCode {

    // Generic / infra
    RESOURCE_NOT_FOUND("error.resource_not_found"),
    VALIDATION_FAILED("error.validation_failed"),
    INVALID_INPUT("error.invalid_input"),
    UNAUTHORIZED("error.unauthorized"),
    FORBIDDEN("error.forbidden"),
    INTERNAL_SERVER_ERROR("error.internal_server_error"),

    // Job posting sample domain
    JOB_POSTING_NOT_FOUND("error.job_posting_not_found"),
    JOB_POSTING_ALREADY_CLOSED("error.job_posting_already_closed"),

    // RBAC / user administration
    USER_NOT_FOUND("error.user_not_found"),
    USER_ALREADY_EXISTS("error.user_already_exists"),
    DEPARTMENT_NOT_FOUND("error.department_not_found"),
    ROLE_NOT_FOUND("error.role_not_found"),
    ROLE_NOT_ASSIGNABLE("error.role_not_assignable"),
    ROLE_NOT_ASSIGNED("error.role_not_assigned"),

    // Keycloak sync (see KeycloakAdminClient)
    KEYCLOAK_ROLE_SYNC_FAILED("error.keycloak_role_sync_failed"),
    KEYCLOAK_USER_SYNC_FAILED("error.keycloak_user_sync_failed");

    private final String key;

    ErrorCode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
