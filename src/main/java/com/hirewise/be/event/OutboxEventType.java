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
    JOB_SUBMITTED_FOR_APPROVAL_EMAIL,
    /** EM-09 (UC-30, BR-REJ-02): automatic polite rejection email after a Recruiter confirms UC-29. */
    APPLICATION_REJECTION_EMAIL,
    /** EM-11 (UC-37 step 4): the secure Offer link plus the e-signature request. */
    OFFER_SENT_EMAIL,
    /** EM-OTP-OFFER (UC-38 step 2, BR-OFFER-03): one-time code guarding the Offer link. */
    OFFER_OTP_EMAIL,
    /** EM-12 (UC-39 step 7): confirms the candidate signed, with the signed PDF link. */
    OFFER_SIGNED_EMAIL
}

