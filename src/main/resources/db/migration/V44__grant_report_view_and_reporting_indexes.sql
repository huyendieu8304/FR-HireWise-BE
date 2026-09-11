-- UC-42 (Source ROI) + UC-43 (Pipeline Velocity), module M20 - Reporting & Analytics.
--
-- 1) BR-RPT-02 noi ro pham vi xem bao cao cua tung vai tro: "Hiring Manager chi thay
--    phong ban phu trach; Recruiter thay Job minh quan ly; HR Admin thay toan he thong".
--    Seed V2 chi cap REPORT_VIEW cho RECRUITER va HIRING_MANAGER, nen HR_ADMIN se an
--    403 ngay o endpoint dau tien du BR-RPT-02 gia dinh ho xem duoc tat ca.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r JOIN permissions p ON (
    p.code = 'REPORT_VIEW' AND r.code = 'HR_ADMIN'
)
ON CONFLICT DO NOTHING;

-- 2) Index phuc vu query bao cao. Khac voi moi query aggregate hien co (deu bo theo
--    dung 1 jobId), bao cao quet nhieu Job trong 1 khoang thoi gian nen can them
--    duong vao theo applied_at va theo (to_stage_id, changed_at).
--    Index co san idx_application_stage_history_app (application_id, changed_at) da
--    phuc vu dung PARTITION BY application_id ORDER BY changed_at cua window function.
CREATE INDEX IF NOT EXISTS idx_applications_applied_at
    ON applications (applied_at);

CREATE INDEX IF NOT EXISTS idx_ash_stage_changed_at
    ON application_stage_history (to_stage_id, changed_at);

CREATE INDEX IF NOT EXISTS idx_job_positions_department_status
    ON job_positions (department_id, status);
