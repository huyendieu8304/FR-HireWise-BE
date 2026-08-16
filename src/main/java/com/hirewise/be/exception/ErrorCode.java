package com.hirewise.be.exception;

/**
 * Danh sách mã lỗi nghiệp vụ của hệ thống. Mỗi ErrorCode ứng với 1 key
 * trong messages.properties để hỗ trợ i18n cho message trả về client.
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
    JOB_POSTING_ALREADY_CLOSED("error.job_posting_already_closed");

    private final String key;

    ErrorCode(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
