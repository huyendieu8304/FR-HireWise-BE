package com.hirewise.be.domain;

/** How an {@link OfferSignature} was produced (LV-22, UC-39 step 2). */
public enum SignatureMethod {
    /** Drawn on the canvas with a mouse/finger. */
    DRAW,
    /** Typed full name used as an initialled signature. */
    TYPE
}
