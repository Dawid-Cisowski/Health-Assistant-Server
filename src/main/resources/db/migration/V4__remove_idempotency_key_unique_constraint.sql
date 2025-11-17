-- Remove unique constraint from idempotency_key to allow duplicate events
-- Events with same idempotency_key can be stored as they may represent updated data

-- Drop the unique index
DROP INDEX IF EXISTS idx_idempotency_key;

-- Create a non-unique index for performance
CREATE INDEX idx_idempotency_key ON health_events(idempotency_key);

-- Remove unique constraint from column definition
ALTER TABLE health_events DROP CONSTRAINT IF EXISTS health_events_idempotency_key_key;

