-- Add missing columns required by Spring Modulith 2.0+
-- These columns were added in newer versions of Spring Modulith for better event tracking
-- Only applies if event_publication table exists (created by Spring Modulith)

DO $$
BEGIN
    -- Check if event_publication table exists
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'event_publication') THEN
        -- Add STATUS column (COMPLETED, FAILED, etc.)
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'event_publication' AND column_name = 'status') THEN
            ALTER TABLE event_publication ADD COLUMN status VARCHAR(255);
        END IF;

        -- Add COMPLETION_ATTEMPTS column to track retry attempts
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'event_publication' AND column_name = 'completion_attempts') THEN
            ALTER TABLE event_publication ADD COLUMN completion_attempts INTEGER DEFAULT 0;
        END IF;

        -- Add LAST_RESUBMISSION_DATE column to track when event was last resubmitted
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'event_publication' AND column_name = 'last_resubmission_date') THEN
            ALTER TABLE event_publication ADD COLUMN last_resubmission_date TIMESTAMP WITH TIME ZONE;
        END IF;

        -- Update existing records to have a status based on completion_date
        UPDATE event_publication
        SET status = CASE
            WHEN completion_date IS NOT NULL THEN 'COMPLETED'
            ELSE 'PENDING'
        END
        WHERE status IS NULL;
    END IF;
END $$;
