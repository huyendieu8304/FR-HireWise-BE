-- UC-44: dong hoac tam dung Job Position dang tuyen (SRS muc 5.2.44,
-- BR-JOB-04/BR-JOB-05). Permission nay chua ton tai o V2 nen phai them moi
-- va cap cho dung 2 role theo bang phan quyen SRS muc 7.4.2:
-- HR_ADMIN (quan tri toan he thong) va RECRUITER (chu Job, con phai qua
-- them RBAC Layer 4 - xem authorization/JobPositionOwnershipResolver).

INSERT INTO permissions (code, description, is_write) VALUES
    ('JOB_CLOSE_PAUSE', 'Close, pause or resume a published Job Position', true)
    ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'JOB_CLOSE_PAUSE'
WHERE r.code IN ('HR_ADMIN', 'RECRUITER')
ON CONFLICT DO NOTHING;
