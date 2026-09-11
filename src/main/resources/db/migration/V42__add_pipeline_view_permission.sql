-- Bugfix: UC-13 (Recruiter chon Pipeline Template + xem truoc Stage roi Gui duyet Job,
-- SubmitForApprovalModal.tsx) calls GET /api/pipeline-templates and
-- GET /api/pipeline-templates/{id}/stages - both were gated by PIPELINE_MANAGE
-- (HR_ADMIN only, see V2), so Recruiter always got 403 trying to list templates to
-- pick from. Split out a read-only permission for these 2 GET endpoints, following
-- the same _VIEW/_MANAGE split already used elsewhere (JOB_VIEW vs JOB_EDIT,
-- USER_VIEW vs USER_CREATE) - PIPELINE_MANAGE stays write-only-capable roles'
-- permission for the actual create/reorder/delete/activate endpoints.
INSERT INTO permissions (code, description, is_write) VALUES
    ('PIPELINE_VIEW', 'View Pipeline Templates and their Stages (read-only)', false)
    ON CONFLICT (code) DO NOTHING;

-- HR_ADMIN also needs PIPELINE_VIEW (not just PIPELINE_MANAGE) since the read
-- endpoints now check PIPELINE_VIEW specifically - without this grant HR_ADMIN's
-- own "Quan ly Pipeline" page would stop being able to list templates/stages.
-- RECRUITER is the actual bugfix target (UC-13).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r JOIN permissions p ON (
    p.code = 'PIPELINE_VIEW' AND r.code IN ('HR_ADMIN', 'RECRUITER')
)
ON CONFLICT DO NOTHING;
