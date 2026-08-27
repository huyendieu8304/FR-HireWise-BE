package com.hirewise.be.domain;

/**
 * Lifecycle status of an {@link Application} (LV-11). {@code HIRED},
 * {@code REFUSED} and {@code WITHDRAWN} are terminal.
 */
public enum ApplicationStatus {
    NEW,
    IN_PROGRESS,
    OFFER_SENT,
    HIRED,
    REFUSED,
    WITHDRAWN
}
