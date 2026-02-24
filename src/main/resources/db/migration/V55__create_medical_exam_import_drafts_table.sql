CREATE TABLE medical_exam_import_drafts (
    id                 UUID        PRIMARY KEY,
    device_id          VARCHAR(255) NOT NULL,
    extracted_data     JSONB        NOT NULL,
    original_filenames JSONB,
    ai_confidence      DECIMAL(3, 2),
    prompt_tokens      BIGINT,
    completion_tokens  BIGINT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_medical_exam_import_drafts_device  ON medical_exam_import_drafts(device_id);
CREATE INDEX idx_medical_exam_import_drafts_cleanup ON medical_exam_import_drafts(status, expires_at);
