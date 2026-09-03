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

    // UC-04 (prerequisite added for UC-13): activate a Pipeline Template (BR-PIPE-01/ME-11)
    PIPELINE_TEMPLATE_NOT_READY_TO_ACTIVATE("error.pipeline_template_not_ready_to_activate"),

    // UC-13 Attach Pipeline Template + submit a Job Position for approval (BR-JOB-01/ME-18)
    JOB_POSITION_NOT_SUBMITTABLE("error.job_position_not_submittable"),
    JOB_MISSING_REQUIRED_FIELDS_FOR_SUBMIT("error.job_missing_required_fields_for_submit"),
    JOB_PIPELINE_TEMPLATE_NOT_ACTIVE("error.job_pipeline_template_not_active"),

    // UC-29 Reject Application (BR-REJ-01/03)
    REJECTION_REASON_NOT_FOUND("error.rejection_reason_not_found"),
    REJECTION_REASON_INACTIVE("error.rejection_reason_inactive"),
    PIPELINE_MISSING_TERMINAL_REJECTED_STAGE("error.pipeline_missing_terminal_rejected_stage"),

    // UC-20 File View URL
    APPLICATION_FILE_NOT_FOUND("error.application_file_not_found"),
    /** BR-STORAGE-02: file is queued locally and not yet synced to Cloud Storage. */
    FILE_NOT_YET_AVAILABLE("error.file_not_yet_available"),

    // UC-18 Calendar integration (BR-INTEG-01/BR-INTEG-02)
    CALENDAR_PROVIDER_UNSUPPORTED("error.calendar_provider_unsupported"),
    CALENDAR_NOT_CONNECTED("error.calendar_not_connected"),
    CALENDAR_TEST_CONNECTION_FAILED("error.calendar_test_connection_failed"),

    // UC-24 Interview Scheduling
    INTERVIEW_STAGE_NOT_INTERVIEW_TYPE("error.interview_stage_not_interview_type"),
    INTERVIEW_INTERVIEWER_NOT_FOUND("error.interview_interviewer_not_found"),
    INTERVIEW_INTERVIEWER_INACTIVE("error.interview_interviewer_inactive"),
    INTERVIEW_TIME_IN_PAST("error.interview_time_in_past"),

    // UC-36 Generate an Offer Letter from a template (BR-OFFER-01/02, EX-01)
    OFFER_TEMPLATE_NOT_FOUND("error.offer_template_not_found"),
    OFFER_TEMPLATE_INACTIVE("error.offer_template_inactive"),
    APPLICATION_NOT_IN_OFFER_STAGE("error.application_not_in_offer_stage"),
    /** EX-01: the Application already has a DRAFT/SENT Offer (BR-OFFER-01). */
    OFFER_ALREADY_ACTIVE("error.offer_already_active"),
    OFFER_EXPIRY_BEFORE_START_DATE("error.offer_expiry_before_start_date"),
    OFFER_NOT_FOUND("error.offer_not_found"),

    // UC-37 Send the Offer link + e-signature request (BR-OFFER-02/03)
    OFFER_NOT_SENDABLE("error.offer_not_sendable"),
    /** ME-32: the answer deadline has passed (BR-OFFER-02). */
    OFFER_EXPIRED("error.offer_expired"),

    // UC-38 Open the secure Offer link + OTP (BR-OFFER-03, ME-32/ME-33)
    /**
     * The link is unknown, malformed, already used, or its secret does not
     * match. Deliberately ONE code for every one of those causes so a caller
     * probing tokens learns nothing about which part was wrong.
     */
    OFFER_LINK_INVALID("error.offer_link_invalid"),
    /** ME-33: wrong or expired one-time code. */
    OFFER_OTP_INVALID("error.offer_otp_invalid"),
    /** Too many wrong codes for the current OTP - a fresh one must be requested. */
    OFFER_OTP_ATTEMPTS_EXCEEDED("error.offer_otp_attempts_exceeded"),
    OFFER_OTP_RESEND_LIMIT("error.offer_otp_resend_limit"),
    /** BR-OFFER-03: contract terms requested before any OTP was verified. */
    OFFER_OTP_REQUIRED("error.offer_otp_required"),

    // UC-39 Electronic signature (BR-OFFER-04, EX-01/ME-34)
    /** ME-34: [Hoan tat ky] pressed with an empty signature. */
    OFFER_SIGNATURE_REQUIRED("error.offer_signature_required"),
    OFFER_SIGNATURE_IMAGE_INVALID("error.offer_signature_image_invalid"),
    /** BR-OFFER-04: a signed offer is locked - it can never be signed twice. */
    OFFER_ALREADY_SIGNED("error.offer_already_signed"),
    OFFER_NOT_SIGNABLE("error.offer_not_signable"),
    PIPELINE_MISSING_TERMINAL_SUCCESS_STAGE("error.pipeline_missing_terminal_success_stage"),

    ;

    private final String key;

    ErrorCode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
