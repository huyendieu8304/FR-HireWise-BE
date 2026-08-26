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

    // UC-17 Applicant Intake (ME-22 in the SRS message catalogue)
    INVALID_CV_FILE("error.invalid_cv_file"),
    PIPELINE_NOT_CONFIGURED("error.pipeline_not_configured"),

    // UC-04 Pipeline Template & Stage configuration
    PIPELINE_TEMPLATE_NOT_FOUND("error.pipeline_template_not_found"),
    PIPELINE_STAGE_CODE_ALREADY_EXISTS("error.pipeline_stage_code_already_exists"),
    PIPELINE_STAGE_NOT_FOUND("error.pipeline_stage_not_found"),

    // UC-05 Reorder Pipeline Stages
    PIPELINE_STAGE_REORDER_MISMATCH("error.pipeline_stage_reorder_mismatch"),

    // UC-06 Delete Pipeline Stage
    PIPELINE_STAGE_HAS_APPLICATIONS("error.pipeline_stage_has_applications"),

    // UC-09 Email Template management (BR-EMAILTPL-01/03/ME-15)
    EMAIL_TEMPLATE_NOT_FOUND("error.email_template_not_found"),
    EMAIL_TEMPLATE_CODE_DUPLICATE("error.email_template_code_duplicate"),
    EMAIL_TEMPLATE_STAGE_CONFLICT("error.email_template_stage_conflict"),

    // UC-22 Kanban Board
    APPLICATION_NOT_FOUND("error.application_not_found"),

    // UC-23 Move Application Stage (BR-KANBAN-01/03)
    APPLICATION_STAGE_TERMINAL("error.application_stage_terminal"),
    PIPELINE_STAGE_INACTIVE("error.pipeline_stage_inactive"),
    INVALID_STAGE_TRANSITION("error.invalid_stage_transition"),

    // UC-12 Draft/edit a Job Position (BR-JOB-02/03/04, ME-19/ME-20)
    JOB_SALARY_RANGE_INVALID("error.job_salary_range_invalid"),
    JOB_DEADLINE_IN_PAST("error.job_deadline_in_past"),
    JOB_POSITION_NOT_EDITABLE("error.job_position_not_editable"),

    ;

    private final String key;

    ErrorCode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
