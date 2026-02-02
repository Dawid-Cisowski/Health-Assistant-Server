-- Add missing columns required by Spring Modulith 2.0+
-- These columns were added in newer versions of Spring Modulith for better event tracking

-- Add STATUS column (COMPLETED, FAILED, etc.)
ALTER TABLE event_publication
ADD COLUMN IF NOT EXISTS status VARCHAR(255);

-- Add COMPLETION_ATTEMPTS column to track retry attempts
ALTER TABLE event_publication
ADD COLUMN IF NOT EXISTS completion_attempts INTEGER DEFAULT 0;

-- Add LAST_RESUBMISSION_DATE column to track when event was last resubmitted
ALTER TABLE event_publication
ADD COLUMN IF NOT EXISTS last_resubmission_date TIMESTAMP WITH TIME ZONE;

-- Update existing records to have a status based on completion_date
UPDATE event_publication
SET status = CASE
    WHEN completion_date IS NOT NULL THEN 'COMPLETED'
    ELSE 'PENDING'
END
WHERE status IS NULL;
