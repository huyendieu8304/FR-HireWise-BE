-- UC-39 (M18 - Offer & e-Signature), BR-OFFER-04. See ERD 03, tables
-- offer_signatures and offer_files.
--
-- offer_signatures is the EVIDENCE of signing, not a flag on `offers`:
-- method, typed/drawn name, when the OTP was verified, the exact signing
-- moment and the signer's IP are what makes the signature defensible later.
-- Keeping it as its own table matches how application_rejections (V28) and
-- job_approvals (V15) record their decisions.
--
-- offer_files links the generated artifacts to the Offer, mirroring
-- application_files (V21). Binary content never lives in this DB - `files`
-- holds only metadata plus the provider-side id.

CREATE TABLE IF NOT EXISTS offer_signatures (
    signature_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_id             UUID NOT NULL REFERENCES offers (offer_id),
    signer_candidate_id  UUID NOT NULL REFERENCES candidates (id),
    -- The signed PDF (file_role = OFFER_SIGNED). Nullable because
    -- BR-STORAGE-02 lets an upload be queued locally when Cloud Storage is
    -- down - the signature is still legally recorded either way.
    signed_file_id       BIGINT REFERENCES files (file_id),
    -- LV-22: DRAW = drawn on canvas, TYPE = typed name.
    method               VARCHAR(10) NOT NULL,
    signer_name          VARCHAR(150) NOT NULL,
    -- Copied off offer_access_tokens at signing time: the token row is
    -- operational state that may be cleaned up, this is evidence.
    otp_verified_at      TIMESTAMPTZ,
    signed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address           INET,
    -- BR-OFFER-04: once signed the offer is locked; a change means a new
    -- revision, never a second signature on the same offer.
    CONSTRAINT uk_offer_signatures_offer UNIQUE (offer_id),
    CONSTRAINT chk_offer_signatures_method CHECK (method IN ('DRAW', 'TYPE'))
);

CREATE TABLE IF NOT EXISTS offer_files (
    offer_file_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_id       UUID NOT NULL REFERENCES offers (offer_id),
    file_id        BIGINT NOT NULL REFERENCES files (file_id),
    file_role      VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_offer_files_role CHECK (file_role IN ('OFFER_DRAFT', 'OFFER_SIGNED'))
);

CREATE INDEX IF NOT EXISTS idx_offer_files_offer ON offer_files (offer_id, file_role);
