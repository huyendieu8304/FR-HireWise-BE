package com.hirewise.be.domain;

/**
 * Lifecycle status of an {@link Offer} (LV-21). {@code SIGNED},
 * {@code DECLINED}, {@code EXPIRED} and {@code CANCELLED} are settled
 * outcomes; only {@code DRAFT} and {@code SENT} count as "active" for
 * BR-OFFER-01 (at most one active Offer per Application).
 * <p>
 * The ERD additionally suggests a {@code VIEWED} value, deliberately not
 * modelled here: LV-21 does not list it, and "the candidate has opened the
 * offer" is already recorded as {@code otp_verified_at} on the access token
 * (UC-38).
 */
public enum OfferStatus {
    DRAFT,
    SENT,
    SIGNED,
    DECLINED,
    EXPIRED,
    CANCELLED
}
