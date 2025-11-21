-- Create sleep projection tables for sleep tracking and analytics

-- Sleep sessions projection table (individual sleep sessions including naps)
CREATE TABLE sleep_sessions_projections (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    date DATE NOT NULL,
    session_number INTEGER NOT NULL,
    sleep_start TIMESTAMP WITH TIME ZONE NOT NULL,
    sleep_end TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    light_sleep_minutes INTEGER,
    deep_sleep_minutes INTEGER,
    rem_sleep_minutes INTEGER,
    awake_minutes INTEGER,
    origin_package VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sleep_session_duration CHECK (duration_minutes >= 0)
);

-- Daily sleep projection table (aggregated daily totals)
CREATE TABLE sleep_daily_projections (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    total_sleep_minutes INTEGER NOT NULL DEFAULT 0,
    sleep_count INTEGER NOT NULL DEFAULT 0,
    first_sleep_start TIMESTAMP WITH TIME ZONE,
    last_sleep_end TIMESTAMP WITH TIME ZONE,
    longest_session_minutes INTEGER,
    shortest_session_minutes INTEGER,
    average_session_minutes INTEGER,
    total_light_sleep_minutes INTEGER DEFAULT 0,
    total_deep_sleep_minutes INTEGER DEFAULT 0,
    total_rem_sleep_minutes INTEGER DEFAULT 0,
    total_awake_minutes INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_daily_total_sleep CHECK (total_sleep_minutes >= 0),
    CONSTRAINT chk_daily_sleep_count CHECK (sleep_count >= 0)
);

-- Indexes for sleep_sessions_projections
CREATE INDEX idx_sleep_sessions_event_id ON sleep_sessions_projections(event_id);
CREATE INDEX idx_sleep_sessions_date ON sleep_sessions_projections(date DESC);
CREATE INDEX idx_sleep_sessions_date_session ON sleep_sessions_projections(date, session_number);
CREATE INDEX idx_sleep_sessions_updated_at ON sleep_sessions_projections(updated_at);

-- Indexes for sleep_daily_projections
CREATE INDEX idx_sleep_daily_date ON sleep_daily_projections(date DESC);
CREATE INDEX idx_sleep_daily_updated_at ON sleep_daily_projections(updated_at);

-- Comments for sleep_sessions_projections
COMMENT ON TABLE sleep_sessions_projections IS 'Individual sleep sessions including main sleep and naps with phase support';
COMMENT ON COLUMN sleep_sessions_projections.event_id IS 'Reference to the source health event';
COMMENT ON COLUMN sleep_sessions_projections.date IS 'Date when sleep started (Europe/Warsaw timezone)';
COMMENT ON COLUMN sleep_sessions_projections.session_number IS 'Session number for the day (1=main sleep, 2+=naps)';
COMMENT ON COLUMN sleep_sessions_projections.sleep_start IS 'Timestamp when sleep started';
COMMENT ON COLUMN sleep_sessions_projections.sleep_end IS 'Timestamp when sleep ended';
COMMENT ON COLUMN sleep_sessions_projections.duration_minutes IS 'Total duration of sleep session in minutes';
COMMENT ON COLUMN sleep_sessions_projections.light_sleep_minutes IS 'Minutes spent in light sleep phase (future support)';
COMMENT ON COLUMN sleep_sessions_projections.deep_sleep_minutes IS 'Minutes spent in deep sleep phase (future support)';
COMMENT ON COLUMN sleep_sessions_projections.rem_sleep_minutes IS 'Minutes spent in REM sleep phase (future support)';
COMMENT ON COLUMN sleep_sessions_projections.awake_minutes IS 'Minutes spent awake during session (future support)';

-- Comments for sleep_daily_projections
COMMENT ON TABLE sleep_daily_projections IS 'Daily aggregated sleep metrics for range views and weekly/monthly analytics';
COMMENT ON COLUMN sleep_daily_projections.date IS 'Date for which sleep is aggregated (Europe/Warsaw timezone)';
COMMENT ON COLUMN sleep_daily_projections.total_sleep_minutes IS 'Total sleep minutes for the entire day (all sessions)';
COMMENT ON COLUMN sleep_daily_projections.sleep_count IS 'Number of sleep sessions (main + naps)';
COMMENT ON COLUMN sleep_daily_projections.first_sleep_start IS 'Timestamp of first sleep session start';
COMMENT ON COLUMN sleep_daily_projections.last_sleep_end IS 'Timestamp of last sleep session end';
COMMENT ON COLUMN sleep_daily_projections.longest_session_minutes IS 'Duration of longest sleep session';
COMMENT ON COLUMN sleep_daily_projections.shortest_session_minutes IS 'Duration of shortest sleep session';
COMMENT ON COLUMN sleep_daily_projections.average_session_minutes IS 'Average session duration';
COMMENT ON COLUMN sleep_daily_projections.total_light_sleep_minutes IS 'Total light sleep minutes across all sessions (future support)';
COMMENT ON COLUMN sleep_daily_projections.total_deep_sleep_minutes IS 'Total deep sleep minutes across all sessions (future support)';
COMMENT ON COLUMN sleep_daily_projections.total_rem_sleep_minutes IS 'Total REM sleep minutes across all sessions (future support)';
COMMENT ON COLUMN sleep_daily_projections.total_awake_minutes IS 'Total awake minutes across all sessions (future support)';
