-- UC-37/UC-38 (M18 - Offer & e-Signature). BR-OFFER-02, BR-OFFER-03.
--
-- One row per Offer, holding everything the candidate's secure link needs.
-- The candidate has no account in this system (SRS section 3.1), so this
-- table IS the authentication for UC-38/UC-39: possession of the link plus
-- a verified OTP, rather than a JWT and the RBAC pipeline.
--
-- Only the hash of the token's secret half is stored (the project's
-- PasswordEncoder, same scheme as activation_tokens in V7) - a leaked DB
-- row alone cannot be replayed as a working offer link. The OTP is hashed
-- for the same reason: it is a short-lived credential, not a data field.

CREATE TABLE IF NOT EXISTS offer_access_tokens (
    -- The "id" half of the "<token_id>:<secret>" opaque token, so a raw
    -- token is looked up in O(1) instead of hashing every row.
    token_id          UUID PRIMARY KEY,
    offer_id          UUID NOT NULL REFERENCES offers (offer_id),
    token_hash        VARCHAR(255) NOT NULL,
    -- Mirrors offers.expires_at (BR-OFFER-02) so link validity and the
    -- answer deadline can never drift apart.
    expires_at        TIMESTAMPTZ NOT NULL,
    otp_code_hash     VARCHAR(255),
    otp_expires_at    TIMESTAMPTZ,
    -- Wrong-code counter, reset whenever a fresh OTP is issued (EX-01/ME-33).
    otp_attempts      INT NOT NULL DEFAULT 0,
    -- Resend throttle for the "Gui lai ma" control of the UC-38 screen.
    otp_sent_count    INT NOT NULL DEFAULT 0,
    otp_last_sent_at  TIMESTAMPTZ,
    -- UC-38 postcondition: the only field that flow writes.
    otp_verified_at   TIMESTAMPTZ,
    -- Set once the offer is signed (UC-39) so the link stops working.
    used_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- BR-OFFER-01 already caps an Application at one active Offer; one live
    -- link per Offer keeps "resend the email" from minting parallel tokens.
    CONSTRAINT uk_offer_access_tokens_offer UNIQUE (offer_id)
);
