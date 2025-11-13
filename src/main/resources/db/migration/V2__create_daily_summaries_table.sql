-- Create daily_summaries table for daily health summaries

CREATE TABLE daily_summaries (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    summary JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE UNIQUE INDEX idx_daily_summaries_date ON daily_summaries(date);
CREATE INDEX idx_daily_summaries_created_at ON daily_summaries(created_at);

-- Add comment
COMMENT ON TABLE daily_summaries IS 'Daily health summaries aggregated from health events';
COMMENT ON COLUMN daily_summaries.date IS 'Date for which the summary is generated (YYYY-MM-DD)';
COMMENT ON COLUMN daily_summaries.summary IS 'Complete daily summary JSON (activity, workouts, sleep, heart, score, notes)';
COMMENT ON COLUMN daily_summaries.created_at IS 'When the summary was first created';
COMMENT ON COLUMN daily_summaries.updated_at IS 'When the summary was last updated';

