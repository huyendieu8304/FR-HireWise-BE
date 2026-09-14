-- Bugfix: Interviewer mo Applicant Card khong thay tab "Phan tich AI" (Match Score,
-- Matched/Missing Skills, AI Summary) vi role INTERVIEWER chua co AI_VIEW (xem V2).
-- Truoc day AI_VIEW gop ca quyen doc lan quyen chay AI (nut "Phan tich lai" va
-- "Quet ca cot"), nen khong the cap thang AI_VIEW cho Interviewer ma khong cho ho
-- chay AI Engine (ton Claude API). Tach ra theo cung pattern _VIEW/_MANAGE o V42:
--   - AI_VIEW (doc): xem ket qua AI Screening - cap them cho INTERVIEWER.
--   - AI_RUN  (ghi): enqueue AI Screening Run - RECRUITER, HIRING_MANAGER (giu
--     nguyen hanh vi hien tai cua 2 role nay).
-- is_write=false co chu dich: truoc day 2 endpoint chay AI check AI_VIEW (doc) nen
-- Layer 3 chi can scope doc - giu nguyen de khong lam 403 Recruiter/HM chi co
-- scope read-only.
INSERT INTO permissions (code, description, is_write) VALUES
    ('AI_RUN', 'Trigger AI screening runs (single re-analyze / scan a Kanban column)', false)
    ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r JOIN permissions p ON (
    p.code = 'AI_RUN' AND r.code IN ('RECRUITER', 'HIRING_MANAGER')
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'AI_VIEW'
WHERE r.code = 'INTERVIEWER'
ON CONFLICT DO NOTHING;
