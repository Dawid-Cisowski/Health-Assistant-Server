DROP INDEX IF EXISTS idx_exercises_muscles;

ALTER TABLE exercises ALTER COLUMN muscles TYPE JSONB USING to_jsonb(muscles);

CREATE INDEX idx_exercises_muscles ON exercises USING GIN(muscles);
