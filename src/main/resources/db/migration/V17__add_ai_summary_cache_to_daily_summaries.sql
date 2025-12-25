ALTER TABLE daily_summaries
    ADD COLUMN ai_summary TEXT,
    ADD COLUMN ai_summary_generated_at TIMESTAMPTZ,
    ADD COLUMN last_event_at TIMESTAMPTZ;

COMMENT ON COLUMN daily_summaries.ai_summary IS 'Cached AI-generated summary text';
COMMENT ON COLUMN daily_summaries.ai_summary_generated_at IS 'When the AI summary was last generated';
COMMENT ON COLUMN daily_summaries.last_event_at IS 'When new events were last added for this date';
