-- Performance indexes for common query patterns

-- Index for conversations lookup by device with ordering
CREATE INDEX IF NOT EXISTS idx_conversations_device_updated
ON conversations(device_id, updated_at DESC);

-- Composite index for health_events date range queries
CREATE INDEX IF NOT EXISTS idx_health_events_occurred_device
ON health_events(occurred_at DESC, device_id);

-- Index for sleep event lookups by device and sleepStart (JSONB)
CREATE INDEX IF NOT EXISTS idx_health_events_sleep_lookup
ON health_events(device_id, event_type)
WHERE event_type = 'SleepSessionRecorded.v1';
