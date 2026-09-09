-- UC-19 (ban sua doi): danh muc kenh chia se tin tuyen dung ra ben ngoai.
--
--  SHARE INTENT LINK: he thong khong tu dang
-- bai ho, ma mo popup chia se san co cua tung nen tang voi mot URL co the
-- crawl duoc Open Graph. Nho vay khong can App Review cua LinkedIn/Facebook -
-- von bat buoc phai co doanh nghiep that va business verification.
--
-- Hau qua ve schema: KHONG dung integration_connections/oauth_tokens o day,
-- vi khong con token nao de luu. Bang nay chi la cau hinh tinh cua tung kenh.

CREATE TABLE IF NOT EXISTS publishing_channels (
    publishing_channel_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                      VARCHAR(30) NOT NULL UNIQUE,
    name                      VARCHAR(100) NOT NULL,
    -- Template share intent cua nen tang. {url} la placeholder DUY NHAT, duoc
    -- thay bang share URL da URL-encode. NULL = kenh khong mo popup (COPY_LINK).
    share_intent_url_template TEXT,
    -- Gia tri utm_source gan vao link, cung la khoa doi chieu voi
    -- applications.source de quy ung vien ve dung kenh (UC-32).
    utm_source                VARCHAR(50) NOT NULL,
    is_enabled                BOOLEAN NOT NULL DEFAULT true,
    display_order             INT NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_publishing_channels_code
        CHECK (code IN ('LINKEDIN', 'FACEBOOK', 'X', 'COPY_LINK'))
);

COMMENT ON COLUMN publishing_channels.share_intent_url_template IS
    'Share intent URL cua nen tang; {url} duoc thay bang share URL da encode. NULL = xu ly phia FE (copy clipboard).';

INSERT INTO publishing_channels (code, name, share_intent_url_template, utm_source, display_order) VALUES
    ('LINKEDIN', 'LinkedIn', 'https://www.linkedin.com/sharing/share-offsite/?url={url}', 'linkedin', 1),
    ('FACEBOOK', 'Facebook', 'https://www.facebook.com/sharer/sharer.php?u={url}', 'facebook', 2),
    ('X', 'X (Twitter)', 'https://twitter.com/intent/tweet?url={url}', 'x', 3),
    ('COPY_LINK', 'Sao chep link', NULL, 'direct', 4)
    ON CONFLICT (code) DO NOTHING;
