package com.hirewise.be.event;

/** Kind of {@link OutboxEvent}, determines which email {@code service.EmailService} method to call. */
public enum OutboxEventType {
    /** "set your password" activation link for a newly-created account. */
    ACTIVATION_EMAIL,
    /**  5 failed logins within 15 minutes. */
    SECURITY_ALERT_EMAIL,
    /** EM-04 (UC-17 step 7): confirms an application was received. */
    APPLICATION_CONFIRMATION_EMAIL
}
