-- UC-38 step 2 (BR-OFFER-03): the one-time code guarding the Offer link.
--
-- The SRS message catalogue has no EM-xx code for this mail - EM-11 carries
-- the link, but nothing carries the OTP - so it gets its own code in the
-- same style as EM-SEC (V27), which was added for the security-lockout
-- notice for the same reason.
--
-- The code itself is a short-lived credential: it appears only in this mail
-- and is stored hashed (offer_access_tokens.otp_code_hash), never logged.

INSERT INTO email_templates (code, name, subject_template, body_template, version, status)
VALUES
(
    'EM-OTP-OFFER',
    'Ma xac thuc xem thu moi lam viec',
    '[{{Company}}] Ma xac thuc thu moi lam viec: {{Otp_Code}}',
    '<p>Xin chao {{Candidate_Name}},</p>' || chr(10) || chr(10) ||
    '<p>Ma xac thuc de xem thu moi lam viec vi tri "{{Job_Title}}" cua ban la:</p>' || chr(10) ||
    '<p style="font-size:24px;font-weight:bold;letter-spacing:4px;">{{Otp_Code}}</p>' || chr(10) || chr(10) ||
    '<p>Ma co hieu luc trong {{Otp_Ttl_Minutes}} phut. Vui long khong chia se ma nay voi bat ky ai.</p>' || chr(10) || chr(10) ||
    '<p>Neu ban khong yeu cau ma nay, hay bo qua email va lien he Recruiter phu trach.</p>' || chr(10) || chr(10) ||
    '<p>Tran trong,<br/>' || chr(10) ||
    '{{Company}}</p>',
    1, 'ACTIVE'
)
ON CONFLICT (code) DO NOTHING;
