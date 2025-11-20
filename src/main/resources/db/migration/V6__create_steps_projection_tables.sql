-- Create steps projection tables for multi-scale step data views

-- Daily steps projection table (aggregated daily totals)
CREATE TABLE steps_daily_projections (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    total_steps INTEGER NOT NULL DEFAULT 0,
    first_step_time TIMESTAMP WITH TIME ZONE,
    last_step_time TIMESTAMP WITH TIME ZONE,
    most_active_hour INTEGER,
    most_active_hour_steps INTEGER,
    active_hours_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Hourly steps projection table (hourly breakdown)
CREATE TABLE steps_hourly_projections (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    hour INTEGER NOT NULL CHECK (hour >= 0 AND hour <= 23),
    step_count INTEGER NOT NULL DEFAULT 0,
    bucket_count INTEGER NOT NULL DEFAULT 0,
    first_bucket_time TIMESTAMP WITH TIME ZONE,
    last_bucket_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_steps_hourly_date_hour UNIQUE(date, hour)
);

-- Indexes for steps_daily_projections
CREATE INDEX idx_steps_daily_date ON steps_daily_projections(date DESC);
CREATE INDEX idx_steps_daily_updated_at ON steps_daily_projections(updated_at);

-- Indexes for steps_hourly_projections
CREATE INDEX idx_steps_hourly_date ON steps_hourly_projections(date DESC);
CREATE INDEX idx_steps_hourly_date_hour ON steps_hourly_projections(date, hour);
CREATE INDEX idx_steps_hourly_updated_at ON steps_hourly_projections(updated_at);

-- Comments for steps_daily_projections
COMMENT ON TABLE steps_daily_projections IS 'Daily aggregated step counts with metadata for range views';
COMMENT ON COLUMN steps_daily_projections.date IS 'Date for which steps are aggregated (Europe/Warsaw timezone)';
COMMENT ON COLUMN steps_daily_projections.total_steps IS 'Total step count for the entire day';
COMMENT ON COLUMN steps_daily_projections.first_step_time IS 'Timestamp of first recorded steps for the day';
COMMENT ON COLUMN steps_daily_projections.last_step_time IS 'Timestamp of last recorded steps for the day';
COMMENT ON COLUMN steps_daily_projections.most_active_hour IS 'Hour (0-23) with highest step count';
COMMENT ON COLUMN steps_daily_projections.most_active_hour_steps IS 'Step count in the most active hour';
COMMENT ON COLUMN steps_daily_projections.active_hours_count IS 'Number of hours with at least 1 step';

-- Comments for steps_hourly_projections
COMMENT ON TABLE steps_hourly_projections IS 'Hourly step counts for detailed daily dashboard view';
COMMENT ON COLUMN steps_hourly_projections.date IS 'Date for which hour belongs (Europe/Warsaw timezone)';
COMMENT ON COLUMN steps_hourly_projections.hour IS 'Hour of day (0-23) in Europe/Warsaw timezone';
COMMENT ON COLUMN steps_hourly_projections.step_count IS 'Total steps recorded during this hour';
COMMENT ON COLUMN steps_hourly_projections.bucket_count IS 'Number of bucketed events contributing to this hour';
COMMENT ON COLUMN steps_hourly_projections.first_bucket_time IS 'Timestamp of first bucket in this hour';
COMMENT ON COLUMN steps_hourly_projections.last_bucket_time IS 'Timestamp of last bucket in this hour';
