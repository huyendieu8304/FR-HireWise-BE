package com.hirewise.be.event;

import java.util.Map;

/**
 * Builds the exact payload {@link Map} each {@link OutboxEventType} needs, in ONE
 * place shared by every publisher.
 */
public final class OutboxPayloads {

    private OutboxPayloads() {
    }

    /** Payload for {@link OutboxEventType#ACTIVATION_EMAIL} - keys read back by {@link OutboxDispatcher}. */
    public static Map<String, Object> activationEmail(String email, String fullName, String activationLink) {
        return Map.of(
                "email", email,
                "fullName", fullName == null ? "" : fullName,
                "activationLink", activationLink);
    }

    /** Payload for {@link OutboxEventType#SECURITY_ALERT_EMAIL} - keys read back by {@link OutboxDispatcher}. */
    public static Map<String, Object> securityAlertEmail(String email, String fullName, String ipAddress) {
        return Map.of(
                "email", email,
                "fullName", fullName == null ? "" : fullName,
                "ipAddress", ipAddress == null ? "" : ipAddress);
    }

    /** Payload for {@link OutboxEventType#APPLICATION_CONFIRMATION_EMAIL} - keys read back by {@link OutboxDispatcher}. */
    public static Map<String, Object> applicationConfirmationEmail(String email, String fullName, String jobTitle) {
        return Map.of(
                "email", email,
                "fullName", fullName == null ? "" : fullName,
                "jobTitle", jobTitle == null ? "" : jobTitle);
    }

    /**
     * Payload for {@link OutboxEventType#JOB_APPROVAL_DECISION_EMAIL} (EM-03, UC-15).
     * Notifies the Recruiter that their job position was either approved or rejected.
     *
     * @param email         recruiter's email address
     * @param recruiterName recruiter's full name (may be null/blank)
     * @param jobTitle      title of the job position being decided on
     * @param approved      {@code true} = Approved, {@code false} = Rejected
     * @param reason        rejection reason; ignored (and may be null) when {@code approved=true}
     */
    public static Map<String, Object> jobApprovalDecisionEmail(String email, String recruiterName,
                                                                String jobTitle, boolean approved,
                                                                String reason) {
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("email", email == null ? "" : email);
        payload.put("recruiterName", recruiterName == null ? "" : recruiterName);
        payload.put("jobTitle", jobTitle == null ? "" : jobTitle);
        payload.put("approved", approved);
        payload.put("reason", reason == null ? "" : reason);
        return java.util.Collections.unmodifiableMap(payload);
    }
}

