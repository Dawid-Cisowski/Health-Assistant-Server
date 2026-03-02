CREATE TABLE health_pillar_ai_summaries (
    device_id              VARCHAR(255) NOT NULL,
    pillar_code            VARCHAR(50)  NOT NULL,
    ai_insight             TEXT,
    generated_at           TIMESTAMPTZ,
    lab_results_updated_at TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_health_pillar_ai_summaries PRIMARY KEY (device_id, pillar_code),
    CONSTRAINT chk_pillar_code CHECK (pillar_code IN (
        'OVERALL','CIRCULATORY','DIGESTIVE','METABOLISM','BLOOD_IMMUNITY','VITAMINS_MINERALS'
    ))
);

CREATE INDEX idx_hpas_device_id ON health_pillar_ai_summaries (device_id);
