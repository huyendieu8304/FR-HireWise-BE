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

    /**
     * Payload for {@link OutboxEventType#JOB_SUBMITTED_FOR_APPROVAL_EMAIL} (EM-02, UC-13).
     * Notifies a Hiring Manager that a Job Position was submitted and needs their review.
     *
     * @param email             Hiring Manager's email address
     * @param hiringManagerName Hiring Manager's full name (may be null/blank)
     * @param jobTitle          title of the job position submitted for approval
     * @param recruiterName     full name of the Recruiter who submitted it (may be null/blank)
     */
    public static Map<String, Object> jobSubmittedForApprovalEmail(
            String email, String hiringManagerName, String jobTitle, String recruiterName) {
        return Map.of(
                "email", email == null ? "" : email,
                "hiringManagerName", hiringManagerName == null ? "" : hiringManagerName,
                "jobTitle", jobTitle == null ? "" : jobTitle,
                "recruiterName", recruiterName == null ? "" : recruiterName);
    }

    /**
     * Payload for {@link OutboxEventType#APPLICATION_REJECTION_EMAIL} (EM-09, UC-30, BR-REJ-02).
     * Automatic polite rejection email sent right after a Recruiter confirms UC-29.
     *
     * @param email         candidate's email address
     * @param candidateName candidate's full name (may be null/blank)
     * @param jobTitle      title of the job the candidate was rejected from
     * @param reasonLabel   standardized rejection reason's display label (BR-REJ-01)
     * @param customMessage optional Recruiter-written note appended on top of the reason; may be null/blank
     */
    public static Map<String, Object> applicationRejectionEmail(
            String email, String candidateName, String jobTitle, String reasonLabel, String customMessage) {
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("email", email == null ? "" : email);
        payload.put("candidateName", candidateName == null ? "" : candidateName);
        payload.put("jobTitle", jobTitle == null ? "" : jobTitle);
        payload.put("reasonLabel", reasonLabel == null ? "" : reasonLabel);
        payload.put("customMessage", customMessage == null ? "" : customMessage);
        return java.util.Collections.unmodifiableMap(payload);
    }

    /**
     * Payload for {@link OutboxEventType#OFFER_SENT_EMAIL} (EM-11, UC-37 step 4).
     * Carries the candidate's secure Offer link and its answer deadline.
     * <p>
     * The raw link token appears here because the outbox row is the only
     * place it survives - the DB keeps just its hash - so it is written
     * once, at send time, and never logged (see {@code OfferSendService}).
     *
     * @param email         candidate's email address
     * @param candidateName candidate's full name (may be null/blank)
     * @param jobTitle      title of the job being offered
     * @param offerLink     full URL of the candidate's secure Offer page
     * @param expiryDate    answer deadline, already formatted for display (BR-OFFER-02)
     * @param recruiterName full name of the Recruiter sending the offer (may be null/blank)
     */
    public static Map<String, Object> offerSentEmail(String email, String candidateName, String jobTitle,
                                                      String offerLink, String expiryDate, String recruiterName) {
        return Map.of(
                "email", email == null ? "" : email,
                "candidateName", candidateName == null ? "" : candidateName,
                "jobTitle", jobTitle == null ? "" : jobTitle,
                "offerLink", offerLink == null ? "" : offerLink,
                "expiryDate", expiryDate == null ? "" : expiryDate,
                "recruiterName", recruiterName == null ? "" : recruiterName);
    }

    /**
     * Payload for {@link OutboxEventType#OFFER_OTP_EMAIL} (UC-38 step 2, BR-OFFER-03).
     * <p>
     * The plaintext OTP travels through the outbox row because that is the
     * only way the dispatcher can render the mail - the DB column holds just
     * its hash. The row is short-lived and the code expires in minutes; it
     * is never written to the application log.
     *
     * @param email         candidate's email address
     * @param candidateName candidate's full name (may be null/blank)
     * @param jobTitle      title of the job being offered
     * @param otpCode       the 6-digit one-time code
     * @param ttlMinutes    how long the code stays valid, for the mail body
     */
    public static Map<String, Object> offerOtpEmail(String email, String candidateName,
                                                     String jobTitle, String otpCode, long ttlMinutes) {
        return Map.of(
                "email", email == null ? "" : email,
                "candidateName", candidateName == null ? "" : candidateName,
                "jobTitle", jobTitle == null ? "" : jobTitle,
                "otpCode", otpCode == null ? "" : otpCode,
                "ttlMinutes", ttlMinutes);
    }

    /**
     * Payload for {@link OutboxEventType#OFFER_SIGNED_EMAIL} (EM-12, UC-39 step 7).
     *
     * @param email          candidate's email address
     * @param candidateName  candidate's full name (may be null/blank)
     * @param jobTitle       title of the job just accepted
     * @param signedAt       signing timestamp, already formatted for display
     * @param startDate      agreed start date, already formatted for display
     * @param signedFileLink where the signed PDF can be retrieved; may be blank
     *                       when the file is still queued locally (BR-STORAGE-02)
     */
    public static Map<String, Object> offerSignedEmail(String email, String candidateName, String jobTitle,
                                                        String signedAt, String startDate, String signedFileLink) {
        return Map.of(
                "email", email == null ? "" : email,
                "candidateName", candidateName == null ? "" : candidateName,
                "jobTitle", jobTitle == null ? "" : jobTitle,
                "signedAt", signedAt == null ? "" : signedAt,
                "startDate", startDate == null ? "" : startDate,
                "signedFileLink", signedFileLink == null ? "" : signedFileLink);
    }
}

