CREATE TABLE examination_attachments (
    id               UUID          PRIMARY KEY,
    examination_id   UUID          NOT NULL REFERENCES examinations(id) ON DELETE CASCADE,
    device_id        VARCHAR(255)  NOT NULL,
    filename         VARCHAR(500)  NOT NULL,
    content_type     VARCHAR(100)  NOT NULL,
    file_size_bytes  BIGINT        NOT NULL,
    storage_provider VARCHAR(20)   NOT NULL DEFAULT 'LOCAL',
    storage_key      VARCHAR(1000) NOT NULL,
    drive_folder_id  VARCHAR(255),
    public_url       VARCHAR(2000),
    attachment_type  VARCHAR(30)   NOT NULL DEFAULT 'DOCUMENT',
    is_primary       BOOLEAN       NOT NULL DEFAULT FALSE,
    description      VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_examination_attachments_exam ON examination_attachments(examination_id);
CREATE INDEX idx_examination_attachments_dev  ON examination_attachments(device_id);
