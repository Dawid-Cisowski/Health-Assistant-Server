ALTER TABLE medical_exam_import_drafts
    ADD COLUMN IF NOT EXISTS stored_files JSONB;
