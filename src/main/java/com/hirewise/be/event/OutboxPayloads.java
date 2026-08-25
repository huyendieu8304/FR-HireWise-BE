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
}
