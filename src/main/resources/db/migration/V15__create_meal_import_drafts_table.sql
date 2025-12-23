-- Meal import drafts table for storing temporary meal data before confirmation
CREATE TABLE meal_import_drafts (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,

    -- Extracted meal data (mutable by user)
    title VARCHAR(255) NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    calories_kcal INTEGER NOT NULL,
    protein_grams INTEGER NOT NULL,
    fat_grams INTEGER NOT NULL,
    carbohydrates_grams INTEGER NOT NULL,
    health_rating VARCHAR(20) NOT NULL,

    -- AI metadata
    confidence DECIMAL(3,2) NOT NULL,

    -- Timestamps
    suggested_occurred_at TIMESTAMPTZ NOT NULL,
    final_occurred_at TIMESTAMPTZ,

    -- Questions and answers from AI clarification flow
    questions JSONB,
    answers JSONB,

    -- Original input reference
    original_description TEXT,

    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_draft_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED'))
);

-- Index for finding drafts by device
CREATE INDEX idx_meal_import_drafts_device ON meal_import_drafts(device_id);

-- Index for cleanup scheduler (find expired pending drafts)
CREATE INDEX idx_meal_import_drafts_expires ON meal_import_drafts(expires_at)
    WHERE status = 'PENDING';

COMMENT ON TABLE meal_import_drafts IS 'Temporary storage for meal import drafts before user confirmation';
