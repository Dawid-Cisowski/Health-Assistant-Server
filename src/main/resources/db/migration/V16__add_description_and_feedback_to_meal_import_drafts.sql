-- Add description (AI analysis breakdown) and user_feedback columns to meal_import_drafts
ALTER TABLE meal_import_drafts
    ADD COLUMN description TEXT,
    ADD COLUMN user_feedback TEXT;

COMMENT ON COLUMN meal_import_drafts.description IS 'AI-generated detailed breakdown of identified meal components and nutritional estimates';
COMMENT ON COLUMN meal_import_drafts.user_feedback IS 'User feedback/corrections for AI re-analysis';
