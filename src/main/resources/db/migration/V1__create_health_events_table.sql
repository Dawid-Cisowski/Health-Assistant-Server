-- Create health_events table for append-only event storage

CREATE TABLE health_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(32) NOT NULL UNIQUE,
    idempotency_key VARCHAR(512) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns
CREATE UNIQUE INDEX idx_idempotency_key ON health_events(idempotency_key);
CREATE INDEX idx_occurred_at ON health_events(occurred_at);
CREATE INDEX idx_event_type ON health_events(event_type);
CREATE INDEX idx_created_at ON health_events(created_at);
CREATE INDEX idx_device_id ON health_events(device_id);

-- GIN index for JSONB payload queries (future projections)
CREATE INDEX idx_payload_gin ON health_events USING GIN (payload);

-- Add comment
COMMENT ON TABLE health_events IS 'Append-only storage for normalized health events from mobile apps';
COMMENT ON COLUMN health_events.event_id IS 'Server-generated unique event ID (e.g., evt_XXXX)';
COMMENT ON COLUMN health_events.idempotency_key IS 'Client-provided idempotency key for deduplication';
COMMENT ON COLUMN health_events.occurred_at IS 'When the event logically occurred (client time)';
COMMENT ON COLUMN health_events.payload IS 'Event-specific payload stored as JSONB';
COMMENT ON COLUMN health_events.created_at IS 'When the event was received by the server';

