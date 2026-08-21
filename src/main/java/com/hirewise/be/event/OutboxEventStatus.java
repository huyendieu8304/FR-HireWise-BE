package com.hirewise.be.event;

/** Delivery status of an {@link OutboxEvent}. */
public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
