ALTER TABLE daily_summaries
    ADD COLUMN ai_report TEXT,
    ADD COLUMN ai_report_generated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
