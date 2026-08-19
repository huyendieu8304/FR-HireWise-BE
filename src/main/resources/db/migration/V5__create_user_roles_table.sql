-- Gan role cho user, co lich su (valid_from/valid_to) thay vi 1 cot role
-- co dinh tren users, vi 1 user co the giu NHIEU role dong thoi (vd vua
-- Recruiter vua Interviewer).

CREATE TABLE IF NOT EXISTS user_roles (
    user_role_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    role_id         BIGINT NOT NULL REFERENCES roles (role_id),
    valid_from      TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles (user_id);
