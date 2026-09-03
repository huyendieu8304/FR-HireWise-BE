package com.hirewise.be.domain;

/**
 * How a scheduled interview will take place (UC-24).
 * {@code ONLINE} — a virtual meeting via a URL link.
 * {@code ONSITE} — in person at a physical location.
 */
public enum InterviewMode {
    ONLINE,
    ONSITE
}
