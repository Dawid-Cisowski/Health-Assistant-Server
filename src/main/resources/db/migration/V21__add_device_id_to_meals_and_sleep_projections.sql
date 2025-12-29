-- Add device_id column to meal and sleep projection tables for multi-device support

-- ============================================
-- MEAL PROJECTIONS
-- ============================================

-- Add device_id column to meal_projections (nullable initially for backfill)
ALTER TABLE meal_projections ADD COLUMN device_id VARCHAR(128);

-- Add device_id column to meal_daily_projections (nullable initially for backfill)
ALTER TABLE meal_daily_projections ADD COLUMN device_id VARCHAR(128);

-- Backfill device_id from existing health_events (single user - all events belong to same device)
UPDATE meal_projections
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

UPDATE meal_daily_projections
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

-- If no events exist yet, set a default device_id for any existing projections
UPDATE meal_projections SET device_id = 'default-device' WHERE device_id IS NULL;
UPDATE meal_daily_projections SET device_id = 'default-device' WHERE device_id IS NULL;

-- Make device_id NOT NULL after backfill
ALTER TABLE meal_projections ALTER COLUMN device_id SET NOT NULL;
ALTER TABLE meal_daily_projections ALTER COLUMN device_id SET NOT NULL;

-- Drop old unique constraint on meal_daily_projections (date only)
ALTER TABLE meal_daily_projections DROP CONSTRAINT IF EXISTS meal_daily_projections_date_key;

-- Create new unique constraints including device_id
ALTER TABLE meal_daily_projections ADD CONSTRAINT uq_meal_daily_device_date UNIQUE(device_id, date);

-- Create new indexes for device_id based queries
CREATE INDEX idx_meal_projections_device_date ON meal_projections(device_id, date DESC);
CREATE INDEX idx_meal_daily_device_date ON meal_daily_projections(device_id, date DESC);

-- Drop old date-only indexes (replaced by device+date indexes)
DROP INDEX IF EXISTS idx_meal_projections_date;
DROP INDEX IF EXISTS idx_meal_daily_date;

-- ============================================
-- SLEEP PROJECTIONS
-- ============================================

-- Add device_id column to sleep_sessions_projections (nullable initially for backfill)
ALTER TABLE sleep_sessions_projections ADD COLUMN device_id VARCHAR(128);

-- Add device_id column to sleep_daily_projections (nullable initially for backfill)
ALTER TABLE sleep_daily_projections ADD COLUMN device_id VARCHAR(128);

-- Backfill device_id from existing health_events
UPDATE sleep_sessions_projections
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

UPDATE sleep_daily_projections
SET device_id = (SELECT DISTINCT device_id FROM health_events WHERE device_id IS NOT NULL LIMIT 1)
WHERE device_id IS NULL;

-- If no events exist yet, set a default device_id for any existing projections
UPDATE sleep_sessions_projections SET device_id = 'default-device' WHERE device_id IS NULL;
UPDATE sleep_daily_projections SET device_id = 'default-device' WHERE device_id IS NULL;

-- Make device_id NOT NULL after backfill
ALTER TABLE sleep_sessions_projections ALTER COLUMN device_id SET NOT NULL;
ALTER TABLE sleep_daily_projections ALTER COLUMN device_id SET NOT NULL;

-- Drop old unique constraint on sleep_daily_projections (date only)
ALTER TABLE sleep_daily_projections DROP CONSTRAINT IF EXISTS sleep_daily_projections_date_key;

-- Create new unique constraints including device_id
ALTER TABLE sleep_daily_projections ADD CONSTRAINT uq_sleep_daily_device_date UNIQUE(device_id, date);

-- Create new indexes for device_id based queries
CREATE INDEX idx_sleep_sessions_device_date ON sleep_sessions_projections(device_id, date DESC);
CREATE INDEX idx_sleep_daily_device_date ON sleep_daily_projections(device_id, date DESC);

-- Drop old date-only indexes (replaced by device+date indexes)
DROP INDEX IF EXISTS idx_sleep_sessions_date;
DROP INDEX IF EXISTS idx_sleep_daily_date;

-- Comments
COMMENT ON COLUMN meal_projections.device_id IS 'Device identifier for multi-device isolation';
COMMENT ON COLUMN meal_daily_projections.device_id IS 'Device identifier for multi-device isolation';
COMMENT ON COLUMN sleep_sessions_projections.device_id IS 'Device identifier for multi-device isolation';
COMMENT ON COLUMN sleep_daily_projections.device_id IS 'Device identifier for multi-device isolation';
