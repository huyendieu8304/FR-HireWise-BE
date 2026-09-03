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

    /**
     * EM-09 (UC-30, BR-REJ-02): automatic polite rejection email sent right after a
     * Recruiter confirms UC-29's Refused decision.
     *
     * @param toEmail       candidate's email address
     * @param candidateName candidate's full name
     * @param jobTitle      title of the job the candidate was rejected from
     * @param reasonLabel   standardized rejection reason's display label (BR-REJ-01)
     * @param customMessage optional Recruiter-written note appended on top of the reason; may be null/blank
     */
    void sendApplicationRejectionEmail(String toEmail, String candidateName,
                                       String jobTitle, String reasonLabel, String customMessage);

    /**
     * EM-11 (UC-37 step 4): the offer letter announcement carrying the
     * candidate's secure link and the deadline to sign it (BR-OFFER-02/03).
     *
     * @param toEmail       candidate's email address
     * @param candidateName candidate's full name
     * @param jobTitle      title of the job being offered
     * @param offerLink     full URL of the candidate's secure Offer page
     * @param expiryDate    answer deadline, already formatted for display
     * @param recruiterName full name of the Recruiter sending the offer
     */
    void sendOfferEmail(String toEmail, String candidateName, String jobTitle,
                        String offerLink, String expiryDate, String recruiterName);
}
