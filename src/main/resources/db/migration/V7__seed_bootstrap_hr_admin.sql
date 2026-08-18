-- Bai toan "con ga qua trung": de goi USER_CREATE (chi HR_ADMIN moi co quyen)
-- can da co san 1 user HR_ADMIN trong he thong. Migration nay seed DUY NHAT
-- 1 tai khoan HR_ADMIN bootstrap dua tren Flyway placeholder, lay tu env var
-- BOOTSTRAP_ADMIN_KEYCLOAK_ID / BOOTSTRAP_ADMIN_EMAIL (xem application.properties,
-- .env.example). Neu placeholder rong (chua cau hinh) thi bo qua, khong loi -
-- vi vay migration nay AN TOAN de chay lai o moi moi truong (local/dev/prod),
-- kho khi chi can dat env var trươc lan khoi dong dau tien cua moi truong do.
DO $$
BEGIN
    IF '${bootstrapAdminKeycloakId}' <> '' THEN
        INSERT INTO users (keycloak_id, email, full_name, status)
        VALUES ('${bootstrapAdminKeycloakId}', '${bootstrapAdminEmail}', 'Bootstrap HR Admin', 'ACTIVE')
        ON CONFLICT (keycloak_id) DO NOTHING;

        INSERT INTO user_roles (user_id, role_id)
        SELECT u.user_id, r.role_id FROM users u, roles r
        WHERE u.keycloak_id = '${bootstrapAdminKeycloakId}' AND r.code = 'HR_ADMIN'
            AND NOT EXISTS (
                SELECT 1 FROM user_roles ur WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id
            );

        INSERT INTO user_access_scopes (user_id, scope_type, can_write)
        SELECT u.user_id, 'SYSTEM', true FROM users u
        WHERE u.keycloak_id = '${bootstrapAdminKeycloakId}'
            AND NOT EXISTS (
                SELECT 1 FROM user_access_scopes s WHERE s.user_id = u.user_id AND s.scope_type = 'SYSTEM'
            );
    END IF;
END $$;
