-- Add version column for optimistic locking
ALTER TABLE meal_import_drafts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add index for cleanup query performance
CREATE INDEX idx_meal_import_drafts_cleanup ON meal_import_drafts (status, expires_at);
