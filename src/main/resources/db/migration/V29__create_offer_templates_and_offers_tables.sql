-- UC-36 (M18 - Offer & e-Signature). See ERD 03_Offer_Documents_Communication
-- section "Offer & e-Signature" and BR-OFFER-01/BR-OFFER-02.
--
-- offer_templates: versioned body of the offer letter/contract. `version`
-- exists so an offer already rendered from a template is never silently
-- reworded when HR edits the wording later - the rendered snapshot lives on
-- `offers.rendered_body` and the template row is only ever superseded by a
-- new version, mirroring how `email_templates` (V18) is versioned.
--
-- offers: the business offer itself (salary, probation rate, start date,
-- answer deadline). Statuses follow LV-21 exactly - the ERD's extra
-- `VIEWED` value is deliberately dropped, "candidate has seen it" is
-- already recorded by `offer_access_tokens.otp_verified_at` (UC-38).

CREATE TABLE IF NOT EXISTS offer_templates (
    offer_template_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- NULL = company-wide template, usable by every department.
    department_id      BIGINT REFERENCES departments (department_id),
    name               VARCHAR(150) NOT NULL,
    body_template      TEXT NOT NULL,
    version            INT NOT NULL DEFAULT 1,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_offer_templates_name_version UNIQUE (name, version),
    CONSTRAINT chk_offer_templates_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_offer_templates_version CHECK (version > 0)
);

CREATE TABLE IF NOT EXISTS offers (
    offer_id            UUID PRIMARY KEY,
    application_id      UUID NOT NULL REFERENCES applications (id),
    offer_template_id   BIGINT NOT NULL REFERENCES offer_templates (offer_template_id),
    created_by_user_id  BIGINT NOT NULL REFERENCES users (user_id),
    salary              NUMERIC(15, 2) NOT NULL,
    -- UC-36 Screen Description field 3: optional, defaults to 85%.
    probation_rate      NUMERIC(5, 2),
    start_date          DATE NOT NULL,
    -- BR-OFFER-02: every offer must carry an answer deadline.
    expires_at          TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL,
    -- Snapshot of offer_templates.body_template rendered with this offer's
    -- data at creation time (UC-36 step 5) - what the candidate reads in
    -- UC-38 and what gets frozen into the signed PDF in UC-39.
    rendered_body       TEXT NOT NULL,
    sent_at             TIMESTAMPTZ,
    signed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_offers_status CHECK (status IN
        ('DRAFT', 'SENT', 'SIGNED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_offers_salary CHECK (salary > 0),
    CONSTRAINT chk_offers_probation_rate CHECK (probation_rate IS NULL
        OR (probation_rate > 0 AND probation_rate <= 100))
);

CREATE INDEX IF NOT EXISTS idx_offers_application_status
    ON offers (application_id, status, created_at);

-- BR-OFFER-01 enforced at the DB level, not only in OfferService: two
-- Recruiters pressing [Tao Offer] at the same moment would both pass an
-- in-Java existence check and create a duplicate active offer. DRAFT and
-- SENT are the only "active" statuses - SIGNED/DECLINED/EXPIRED/CANCELLED
-- are all settled outcomes that must not block a renegotiated offer.
CREATE UNIQUE INDEX IF NOT EXISTS uk_offers_one_active_per_application
    ON offers (application_id)
    WHERE status IN ('DRAFT', 'SENT');

-- No use case in the SRS manages offer_templates (UC-35 is Confirm Booking,
-- unrelated), yet UC-36 step 2 needs a non-empty dropdown - so seed the
-- default company-wide template here, same as V23 seeds EM-01..EM-13.
-- Placeholders match OfferTemplateRenderer's {{Var}} syntax, identical to
-- the one email templates already use.
INSERT INTO offer_templates (department_id, name, body_template, version, status)
SELECT NULL,
       'Thu moi lam viec chuan',
       '<h2>THU MOI LAM VIEC</h2>' ||
       '<p>Kinh gui <strong>{{Candidate_Name}}</strong>,</p>' ||
       '<p>{{Company}} tran trong moi ban gia nhap doi ngu o vi tri ' ||
       '<strong>{{Job_Title}}</strong> voi cac dieu khoan sau:</p>' ||
       '<ul>' ||
       '<li>Muc luong chinh thuc: <strong>{{Salary}}</strong></li>' ||
       '<li>Ty le huong luong thu viec: <strong>{{Probation_Rate}}</strong></li>' ||
       '<li>Ngay nhan viec du kien: <strong>{{Start_Date}}</strong></li>' ||
       '<li>Han tra loi thu moi: <strong>{{Expiry_Date}}</strong></li>' ||
       '</ul>' ||
       '<p>Vui long xac nhan bang chu ky dien tu truoc han tra loi noi tren. ' ||
       'Sau thoi diem do thu moi se tu dong het hieu luc.</p>' ||
       '<p>Tran trong,<br/>{{Recruiter_Name}} - {{Company}}</p>',
       1,
       'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM offer_templates WHERE name = 'Thu moi lam viec chuan' AND version = 1
);
