CREATE TABLE health_reports (
    id              BIGSERIAL PRIMARY KEY,
    device_id       VARCHAR(255) NOT NULL,
    report_type     VARCHAR(20) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    ai_summary      TEXT,
    short_summary   VARCHAR(500),
    goals_json      JSONB,
    goals_achieved  INT NOT NULL DEFAULT 0,
    goals_total     INT NOT NULL DEFAULT 0,
    comparison_json JSONB,
    data_json       JSONB,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_health_reports_device_type_period
        UNIQUE (device_id, report_type, period_start, period_end)
);

CREATE INDEX idx_health_reports_device_type
    ON health_reports (device_id, report_type);

CREATE INDEX idx_health_reports_device_type_generated
    ON health_reports (device_id, report_type, generated_at DESC);
