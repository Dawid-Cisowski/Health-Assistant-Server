CREATE TABLE lab_results (
    id                     UUID         PRIMARY KEY,
    examination_id         UUID         NOT NULL REFERENCES examinations(id) ON DELETE CASCADE,
    device_id              VARCHAR(255) NOT NULL,
    marker_code            VARCHAR(100) NOT NULL,
    marker_name            VARCHAR(255) NOT NULL,
    category               VARCHAR(100),
    value_numeric          DECIMAL(12, 4),
    unit                   VARCHAR(50),
    original_value_numeric DECIMAL(12, 4),
    original_unit          VARCHAR(50),
    conversion_applied     BOOLEAN      NOT NULL DEFAULT FALSE,
    ref_range_low          DECIMAL(12, 4),
    ref_range_high         DECIMAL(12, 4),
    ref_range_text         VARCHAR(255),
    default_ref_range_low  DECIMAL(12, 4),
    default_ref_range_high DECIMAL(12, 4),
    value_text             VARCHAR(500),
    flag                   VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    sort_order             INTEGER      NOT NULL DEFAULT 0,
    performed_at           TIMESTAMPTZ,
    date                   DATE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_lab_results_examination ON lab_results(examination_id);
CREATE INDEX idx_lab_results_trend       ON lab_results(device_id, marker_code, date ASC);
CREATE INDEX idx_lab_results_device      ON lab_results(device_id);
