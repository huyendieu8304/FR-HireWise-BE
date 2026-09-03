package com.hirewise.be.domain;

/**
 * Whether an {@link OfferTemplate} version is still offered in the UC-36
 * template dropdown. A template is never edited in place once offers have
 * been rendered from it - it is set {@code INACTIVE} and superseded by a
 * new {@code version} row.
 */
public enum OfferTemplateStatus {
    ACTIVE,
    INACTIVE
}
