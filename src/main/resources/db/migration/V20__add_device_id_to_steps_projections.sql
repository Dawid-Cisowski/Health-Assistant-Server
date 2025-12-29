-- Add device_id column to steps projection tables for multi-device support

-- Add device_id column to steps_hourly_projections (nullable initially for backfill)
ALTER TABLE steps_hourly_projections ADD COLUMN device_id VARCHAR(128);

-- Add device_id column to steps_daily_projections (nullable initially for backfill)
ALTER TABLE steps_daily_projections ADD COLUMN device_id VARCHAR(128);

-- Backfill device_id from existing health_events (single user - all events belong to same device)
UPDATE steps_hourly_projections
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

UPDATE steps_daily_projections
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

-- If no events exist yet, set a default device_id for any existing projections
UPDATE steps_hourly_projections SET device_id = 'default-device' WHERE device_id IS NULL;
UPDATE steps_daily_projections SET device_id = 'default-device' WHERE device_id IS NULL;

-- Make device_id NOT NULL after backfill
ALTER TABLE steps_hourly_projections ALTER COLUMN device_id SET NOT NULL;
ALTER TABLE steps_daily_projections ALTER COLUMN device_id SET NOT NULL;

-- Drop old unique constraint on steps_daily_projections (date only)
ALTER TABLE steps_daily_projections DROP CONSTRAINT IF EXISTS steps_daily_projections_date_key;

-- Drop old unique constraint on steps_hourly_projections (date, hour)
ALTER TABLE steps_hourly_projections DROP CONSTRAINT IF EXISTS uq_steps_hourly_date_hour;

-- Create new unique constraints including device_id
ALTER TABLE steps_daily_projections ADD CONSTRAINT uq_steps_daily_device_date UNIQUE(device_id, date);
ALTER TABLE steps_hourly_projections ADD CONSTRAINT uq_steps_hourly_device_date_hour UNIQUE(device_id, date, hour);

-- Create new indexes for device_id based queries
CREATE INDEX idx_steps_daily_device_date ON steps_daily_projections(device_id, date DESC);
CREATE INDEX idx_steps_hourly_device_date ON steps_hourly_projections(device_id, date DESC);
CREATE INDEX idx_steps_hourly_device_date_hour ON steps_hourly_projections(device_id, date, hour);

-- Drop old date-only indexes (replaced by device+date indexes)
DROP INDEX IF EXISTS idx_steps_daily_date;
DROP INDEX IF EXISTS idx_steps_hourly_date;
DROP INDEX IF EXISTS idx_steps_hourly_date_hour;

-- Comments
COMMENT ON COLUMN steps_hourly_projections.device_id IS 'Device identifier for multi-device isolation';
COMMENT ON COLUMN steps_daily_projections.device_id IS 'Device identifier for multi-device isolation';
