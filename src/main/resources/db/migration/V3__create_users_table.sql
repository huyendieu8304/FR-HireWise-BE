-- Ban ghi noi bo cua user, quan ly boi chinh app (Spring Security + Argon2id
-- password hash luu trong auth_identities) - khong con phu thuoc IdP ngoai
-- (Keycloak) nua. Can thiet de RBAC lop 1 (Authentication Freshness -
-- BR-AUTH-07: kiem tra status=ACTIVE tai thoi diem xu ly request, khong chi
-- tai thoi diem token duoc cap) va lop 3 (Access Scope) hoat dong, vi JWT
-- chi mang thong tin "dung tai thoi diem cap token", khong the tu cap nhat
-- khi HR Admin khoa tai khoan.
--
-- department_id o day CHI la phong ban to chuc CHINH (dung cho bao cao/org
-- chart), KHONG phai pham vi truy cap du lieu - xem BR-RBAC-05, user_access_scopes.
--
-- Khong luu credential (password/token) o day - xem auth_identities: 1 user
-- co the co nhieu login method cung luc (LOCAL + GOOGLE, UC-01 AF-01).
--
-- status mac dinh INVITED: tai khoan duoc HR Admin tao truoc (UC-02), chua
-- co password su dung duoc cho toi khi nguoi dung tu kich hoat qua link EM-01
-- (xem activation_tokens) - khac voi mo hinh cu (Keycloak) la ACTIVE ngay.

CREATE TABLE IF NOT EXISTS users (
    user_id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                   VARCHAR(255) NOT NULL,
    full_name               VARCHAR(255),
    department_id           BIGINT REFERENCES departments (department_id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    last_authenticated_at   TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
