CREATE TABLE examinations (
    id                  UUID         PRIMARY KEY,
    device_id           VARCHAR(255) NOT NULL,
    exam_type_code      VARCHAR(100) NOT NULL REFERENCES exam_type_definitions(code),
    title               VARCHAR(500) NOT NULL,
    performed_at        TIMESTAMPTZ,
    results_received_at TIMESTAMPTZ,
    date                DATE         NOT NULL,
    laboratory          VARCHAR(255),
    ordering_doctor     VARCHAR(255),
    status              VARCHAR(30)  NOT NULL DEFAULT 'COMPLETED',
    display_type        VARCHAR(30)  NOT NULL,
    specialties         JSONB        NOT NULL DEFAULT '[]',
    notes               TEXT,
    summary             TEXT,
    report_text         TEXT,
    findings            JSONB,
    conclusions         TEXT,
    recommendations     TEXT,
    source              VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_examinations_device_date    ON examinations(device_id, date DESC);
CREATE INDEX idx_examinations_specialties    ON examinations USING GIN(specialties);
CREATE INDEX idx_examinations_device_type    ON examinations(device_id, exam_type_code);
