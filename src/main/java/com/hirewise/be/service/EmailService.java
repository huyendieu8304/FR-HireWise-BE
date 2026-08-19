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
}
