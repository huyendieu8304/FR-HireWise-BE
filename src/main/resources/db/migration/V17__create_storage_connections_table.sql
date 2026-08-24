-- UC-07/UC-08: the cloud-storage-specific connection (Google Drive/Dropbox),
-- one-to-one with an integration_connections row. Connection status itself is
-- NOT duplicated here - callers read integration_connections.status via the
-- integration_connection_id relationship, so the two can never drift apart.

CREATE TABLE IF NOT EXISTS storage_connections (
    storage_connection_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_connection_id   BIGINT NOT NULL REFERENCES integration_connections (integration_connection_id),
    provider                    VARCHAR(20) NOT NULL,
    root_folder_id              VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_storage_connections_provider CHECK (provider IN ('GOOGLE_DRIVE', 'DROPBOX')),
    CONSTRAINT uk_storage_connections_connection UNIQUE (integration_connection_id)
);

COMMENT ON COLUMN storage_connections.root_folder_id IS 'BR-STORAGE-03: root folder created on connect; per-application subfolders are created under it at upload time.';
