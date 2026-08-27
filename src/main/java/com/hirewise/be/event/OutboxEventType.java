package com.hirewise.be.event;

/** Kind of {@link OutboxEvent}, determines which email {@code service.EmailService} method to call. */
public enum OutboxEventType {
    /** "set your password" activation link for a newly-created account. */
    ACTIVATION_EMAIL,
    /**  5 failed logins within 15 minutes. */
    SECURITY_ALERT_EMAIL,
    /** EM-04 (UC-17 step 7): confirms an application was received. */
    APPLICATION_CONFIRMATION_EMAIL,
    /** EM-03 (UC-15 step 3 / AF-01 step 4): notifies the Recruiter of the Hiring Manager's decision. */
    JOB_APPROVAL_DECISION_EMAIL,
    /** EM-02 (UC-13 step 5): notifies a Hiring Manager that a Job Position needs their approval. */
    JOB_SUBMITTED_FOR_APPROVAL_EMAIL
}

