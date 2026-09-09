package com.hirewise.be.domain;

/**
 * Lifecycle status of a Self-service booking request (UC-25).
 */
public enum InterviewBookingRequestStatus {
    /** At least one slot is still OPEN and the token has not expired. */
    OPEN,
    /** A candidate has confirmed a slot and an interview was created. */
    COMPLETED,
    /** All slots have been filled or the token has passed its {@code expires_at}. */
    EXPIRED,
    /** Recruiter manually cancelled the request before a candidate confirmed. */
    CANCELLED
}
