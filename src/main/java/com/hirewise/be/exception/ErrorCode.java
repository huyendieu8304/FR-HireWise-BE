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

    // Job position sample domain
    JOB_POSITION_NOT_FOUND("error.job_position_not_found"),
    JOB_POSITION_ALREADY_CLOSED("error.job_position_already_closed"),

    // RBAC / user administration
    USER_NOT_FOUND("error.user_not_found"),
    USER_ALREADY_EXISTS("error.user_already_exists"),
    DEPARTMENT_NOT_FOUND("error.department_not_found"),
    ROLE_NOT_FOUND("error.role_not_found"),
    ROLE_NOT_ASSIGNABLE("error.role_not_assignable"),
    ROLE_NOT_ASSIGNED("error.role_not_assigned"),

    // UC-01 Authentication (ME-06/ME-07/ME-08 in the SRS message catalogue)
    INVALID_CREDENTIALS("error.invalid_credentials"),
    ACCOUNT_LOCKED("error.account_locked"),
    ACCOUNT_NOT_ACTIVATED("error.account_not_activated"),
    INVALID_OR_EXPIRED_TOKEN("error.invalid_or_expired_token"),
    TOO_MANY_REQUESTS("error.too_many_requests"),

    // UC-07/UC-08 Cloud Storage integration
    INTEGRATION_PROVIDER_UNSUPPORTED("error.integration_provider_unsupported"),
    INTEGRATION_NOT_CONNECTED("error.integration_not_connected"),

    // UC-09 Email Template management (BR-EMAILTPL-01/03/ME-15)
    EMAIL_TEMPLATE_NOT_FOUND("error.email_template_not_found"),
    EMAIL_TEMPLATE_CODE_DUPLICATE("error.email_template_code_duplicate"),
    EMAIL_TEMPLATE_STAGE_CONFLICT("error.email_template_stage_conflict");

    private final String key;

    ErrorCode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
