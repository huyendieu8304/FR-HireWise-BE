CREATE TABLE IF NOT EXISTS departments (
    department_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    parent_department_id   BIGINT REFERENCES departments (department_id),
    is_active               BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_departments_parent ON departments (parent_department_id);
