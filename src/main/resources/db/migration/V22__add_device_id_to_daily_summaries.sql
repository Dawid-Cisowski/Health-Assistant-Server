-- Add device_id column to daily_summaries table for multi-device support

-- Add device_id column (nullable initially for backfill)
ALTER TABLE daily_summaries ADD COLUMN device_id VARCHAR(128);

-- Backfill device_id from existing health_events (single user - all summaries belong to same device)
UPDATE daily_summaries
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

-- If no events exist yet, set a default device_id for any existing summaries
UPDATE daily_summaries SET device_id = 'default-device' WHERE device_id IS NULL;

-- Make device_id NOT NULL after backfill
ALTER TABLE daily_summaries ALTER COLUMN device_id SET NOT NULL;

-- Drop old unique constraint on date
ALTER TABLE daily_summaries DROP CONSTRAINT IF EXISTS daily_summaries_date_key;
DROP INDEX IF EXISTS idx_daily_summaries_date;

-- Create new unique constraint including device_id
ALTER TABLE daily_summaries ADD CONSTRAINT uq_daily_summaries_device_date UNIQUE(device_id, date);

-- Create new index for device_id based queries
CREATE INDEX idx_daily_summaries_device_date ON daily_summaries(device_id, date DESC);

-- Comment
COMMENT ON COLUMN daily_summaries.device_id IS 'Device identifier for multi-device isolation';
