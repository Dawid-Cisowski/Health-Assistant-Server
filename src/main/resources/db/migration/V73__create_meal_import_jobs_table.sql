CREATE TABLE meal_import_jobs (
    id                  UUID PRIMARY KEY,
    device_id           VARCHAR(255) NOT NULL,
    job_type            VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    description         TEXT,
    image_data          JSONB,
    result              TEXT,
    error_message       TEXT,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meal_import_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    CONSTRAINT chk_meal_import_job_type   CHECK (job_type IN ('IMPORT', 'ANALYZE'))
);
CREATE INDEX idx_meal_import_jobs_device  ON meal_import_jobs(device_id);
CREATE INDEX idx_meal_import_jobs_cleanup ON meal_import_jobs(status, expires_at);
