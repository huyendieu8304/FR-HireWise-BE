package com.hirewise.be.domain;

/**
 * What an {@link ApplicationFile} represents (LV-25, restricted to the roles
 * relevant to Applicant Intake - the Offer-specific roles from that same list
 * belong to {@code offer_files}, out of scope through UC-17).
 */
public enum ApplicationFileRole {
    CV,
    COVER_LETTER,
    PORTFOLIO
}
