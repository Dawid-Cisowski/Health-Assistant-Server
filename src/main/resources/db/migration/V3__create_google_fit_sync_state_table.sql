-- Create google_fit_sync_state table for tracking Google Fit synchronization state

CREATE TABLE google_fit_sync_state (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL UNIQUE,
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE UNIQUE INDEX idx_google_fit_sync_state_user_id ON google_fit_sync_state(user_id);
CREATE INDEX idx_google_fit_sync_state_last_synced_at ON google_fit_sync_state(last_synced_at);

-- Add comment
COMMENT ON TABLE google_fit_sync_state IS 'Tracks the last synchronization timestamp for Google Fit data per user';
COMMENT ON COLUMN google_fit_sync_state.user_id IS 'User identifier (default: "default")';
COMMENT ON COLUMN google_fit_sync_state.last_synced_at IS 'Last successful synchronization timestamp';

