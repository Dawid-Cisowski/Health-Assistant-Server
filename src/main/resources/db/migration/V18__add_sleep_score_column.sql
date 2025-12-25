-- Add sleep_score column to sleep projection tables

ALTER TABLE sleep_sessions_projections
ADD COLUMN sleep_score INTEGER;

COMMENT ON COLUMN sleep_sessions_projections.sleep_score IS 'Sleep quality score 0-100 (from app or auto-calculated)';

-- Add average sleep score to daily projections
ALTER TABLE sleep_daily_projections
ADD COLUMN average_sleep_score INTEGER;

COMMENT ON COLUMN sleep_daily_projections.average_sleep_score IS 'Average sleep score for the day';
