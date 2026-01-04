-- V27: Add columns for event sourcing with compensation events
-- These columns track event deletion and superseding without physical deletion

-- Add status columns to health_events table
ALTER TABLE health_events ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE health_events ADD COLUMN deleted_by_event_id VARCHAR(32) DEFAULT NULL;
ALTER TABLE health_events ADD COLUMN superseded_by_event_id VARCHAR(32) DEFAULT NULL;

-- Index for efficient filtering of active events (partial index)
CREATE INDEX idx_health_events_active ON health_events(device_id, event_type, occurred_at)
    WHERE deleted_at IS NULL AND superseded_by_event_id IS NULL;

-- Index for looking up compensation events by target event ID
CREATE INDEX idx_health_events_target_event ON health_events((payload->>'targetEventId'));

-- Comments for documentation
COMMENT ON COLUMN health_events.deleted_at IS 'Timestamp when event was marked as deleted via EventDeleted.v1';
COMMENT ON COLUMN health_events.deleted_by_event_id IS 'Event ID of the EventDeleted.v1 that deleted this event';
COMMENT ON COLUMN health_events.superseded_by_event_id IS 'Event ID of the EventCorrected.v1 that replaced this event';
