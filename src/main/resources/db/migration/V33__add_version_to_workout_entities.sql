-- Add version column for optimistic locking to workout exercise projections
ALTER TABLE workout_exercise_projections ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add version column for optimistic locking to workout set projections
ALTER TABLE workout_set_projections ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add version column for optimistic locking to routine exercises
ALTER TABLE routine_exercises ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
