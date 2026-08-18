-- Ban sao noi bo cua user tren Keycloak, can thiet de RBAC lop 1 (Authentication
-- Freshness - BR-AUTH-07: kiem tra status=ACTIVE tai thoi diem xu ly request,
-- khong chi tai thoi diem token duoc cap) va lop 3 (Access Scope) hoat dong,
-- vi JWT chi mang thong tin "dung tai thoi diem cap token", khong the tu cap
-- nhat khi HR Admin khoa tai khoan.
--
-- department_id o day CHI la phong ban to chuc CHINH (dung cho bao cao/org chart), KHONG phai pham vi truy cap du lieu - xem BR-RBAC-05, user_access_scopes.

CREATE TABLE IF NOT EXISTS users (
    user_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keycloak_id     VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    department_id   BIGINT REFERENCES departments (department_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
