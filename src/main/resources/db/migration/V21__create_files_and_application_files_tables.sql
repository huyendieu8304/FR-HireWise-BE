-- UC-17: CV upload. Binary content lives on Cloud Storage (UC-07); this
-- database only ever stores file metadata plus the link to its owning
-- Application - see ERD 03_Offer_Documents_Communication.

CREATE TABLE IF NOT EXISTS files (
    file_id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_connection_id  BIGINT NOT NULL REFERENCES storage_connections (storage_connection_id),
    file_name               VARCHAR(255) NOT NULL,
    mime_type                VARCHAR(100) NOT NULL,
    size_bytes                BIGINT NOT NULL,
    external_file_id           VARCHAR(255) NOT NULL,
    checksum_sha256             VARCHAR(64),
    status                       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_files_connection_external_id UNIQUE (storage_connection_id, external_file_id),
    CONSTRAINT chk_files_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED'))
);

CREATE TABLE IF NOT EXISTS application_files (
    application_file_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id       UUID NOT NULL REFERENCES applications (id),
    file_id               BIGINT NOT NULL REFERENCES files (file_id),
    file_role              VARCHAR(30) NOT NULL,
    is_primary              BOOLEAN NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_application_files_role CHECK (file_role IN ('CV', 'COVER_LETTER', 'PORTFOLIO'))
);

CREATE INDEX IF NOT EXISTS idx_application_files_application ON application_files (application_id);
