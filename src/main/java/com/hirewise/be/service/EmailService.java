package com.hirewise.be.service;

/**
 * Sends the transactional emails the auth/user-management use cases need.
 * Only ever called by {@link com.hirewise.be.event.OutboxDispatcher} - never directly from a
 * request thread - so a slow/unreachable SMTP server can't make a user's
 * HTTP request hang (see {@code event.OutboxEvent}).
 */
public interface EmailService {

    /** "set your password" first-activation link for a newly-created account. */
    void sendActivationEmail(String toEmail, String fullName, String activationLink);

    /** 5 failed login attempts within 15 minutes - notify the account owner. */
    void sendSecurityAlertEmail(String toEmail, String fullName, String ipAddress);

    /** EM-04 (UC-17 step 7): confirms a candidate's application to {@code jobTitle} was received. */
    void sendApplicationConfirmationEmail(String toEmail, String fullName, String jobTitle);

    /**
     * EM-03 (UC-15 step 3 / AF-01 step 4): notifies the Recruiter of the Hiring Manager's
     * Approve or Reject decision on their job position.
     *
     * @param toEmail       recruiter's email address
     * @param recruiterName recruiter's full name
     * @param jobTitle      title of the job that was decided on
     * @param approved      {@code true} = Approved, {@code false} = Rejected
     * @param reason        rejection reason; may be null/blank when {@code approved=true}
     */
    void sendJobApprovalDecisionEmail(String toEmail, String recruiterName,
                                      String jobTitle, boolean approved, String reason);

    /**
     * Sends an email dynamically using a registered {@code EmailTemplate} by code (e.g. EM-01..EM-13).
     *
     * @param toEmail      recipient's email address
     * @param templateCode template code in {@code email_templates} table (e.g. EM-01, EM-02, ...)
     * @param variables    placeholder variables mapped to values (e.g. Candidate_Name -> "Nguyen Van A")
     */
    void sendTemplateEmail(String toEmail, String templateCode, java.util.Map<String, String> variables);

    /**
     * EM-02 (UC-13 step 5): notifies a Hiring Manager that a Job Position was
     * submitted for approval and needs their review.
     *
     * @param toEmail           Hiring Manager's email address
     * @param hiringManagerName Hiring Manager's full name
     * @param jobTitle          title of the job position submitted for approval
     * @param recruiterName     full name of the Recruiter who submitted it
     */
    void sendJobSubmittedForApprovalEmail(String toEmail, String hiringManagerName,
                                          String jobTitle, String recruiterName);
}
