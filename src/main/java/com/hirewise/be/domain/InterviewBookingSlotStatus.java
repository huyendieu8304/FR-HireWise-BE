package com.hirewise.be.domain;

/**
 * Lifecycle status of a single candidate-selectable time slot (UC-25/UC-34/UC-35).
 */
public enum InterviewBookingSlotStatus {
    /** Available for any candidate to pick. */
    OPEN,
    /**
     * Temporarily locked in a short-lived transaction window ({@code held_until})
     * while the candidate is on the confirm screen — prevents two candidates
     * from grabbing the same slot simultaneously (BR-SCHED-02).
     */
    HELD,
    /** A candidate has confirmed this slot and an {@link Interview} has been created. */
    CONFIRMED,
    /** Alias for CONFIRMED. */
    BOOKED,
    /** Interviewer has another interview scheduled at this slot (grayed out on candidate page). */
    BUSY
}
