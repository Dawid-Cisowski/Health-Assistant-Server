ALTER TABLE health_events ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_health_events_device_occurred ON health_events(device_id, occurred_at);
