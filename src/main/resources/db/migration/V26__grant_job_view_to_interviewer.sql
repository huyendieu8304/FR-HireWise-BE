-- "Vi tri tuyen dung" (danh sach Job + tim kiem theo ten): Interviewer can
-- xem duoc trang nay de tim dung Job dang phong van, khong chi xem
-- Application cua rieng minh (APPLICATION_VIEW da co tu V2). Cap them
-- JOB_VIEW cho role INTERVIEWER - khong doi permission nao khac, khong anh
-- huong JOB_APPROVE/JOB_EDIT/... la cac quyen ghi chi HR_ADMIN/RECRUITER/
-- HIRING_MANAGER moi co.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'JOB_VIEW'
WHERE r.code = 'INTERVIEWER'
ON CONFLICT DO NOTHING;
