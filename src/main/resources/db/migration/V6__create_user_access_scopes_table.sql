-- RBAC layer 3 (Access Scope). 1 user co the co NHIEU dong scope_type=DEPARTMENT
-- tro toi nhieu phong ban khac nhau cung luc (BR-RBAC-05: UNION khi check quyen).
-- include_sub_departments=true (mac dinh) nghia la scope ke thua xuong ca
-- phong ban con (BR-RBAC-06, tinh bang recursive CTE tai thoi diem check,
-- xem DepartmentRepository - KHONG luu cung danh sach id con o day).

CREATE TABLE IF NOT EXISTS user_access_scopes (
    scope_id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    scope_type                  VARCHAR(20) NOT NULL, -- SYSTEM | DEPARTMENT | JOB
    department_id               BIGINT REFERENCES departments (department_id),
    job_id                      BIGINT REFERENCES job_postings (id),
    include_sub_departments     BOOLEAN NOT NULL DEFAULT true,
    can_write                   BOOLEAN NOT NULL DEFAULT false,
    valid_from                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to                    TIMESTAMPTZ,
    CONSTRAINT chk_scope_type CHECK (scope_type IN ('SYSTEM', 'DEPARTMENT', 'JOB'))
);

CREATE INDEX IF NOT EXISTS idx_user_access_scopes_user ON user_access_scopes (user_id);
