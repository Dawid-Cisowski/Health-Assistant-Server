-- Add exercise_id column to workout_exercise_projections (nullable initially for backward compatibility)
ALTER TABLE workout_exercise_projections
ADD COLUMN exercise_id VARCHAR(50) REFERENCES exercises(id);

-- Add exercise_id column to workout_set_projections
ALTER TABLE workout_set_projections
ADD COLUMN exercise_id VARCHAR(50) REFERENCES exercises(id);

-- Create indexes for efficient exercise_id lookups
CREATE INDEX idx_workout_exercise_exercise_id ON workout_exercise_projections(exercise_id);
CREATE INDEX idx_workout_set_exercise_id ON workout_set_projections(exercise_id);

-- Composite index for statistics queries (device + exercise_id + date)
CREATE INDEX idx_workout_exercise_device_exercise_date
ON workout_exercise_projections(exercise_id)
INCLUDE (workout_id);

-- Add is_auto_created column to exercises table to track AI-created exercises
ALTER TABLE exercises ADD COLUMN is_auto_created BOOLEAN NOT NULL DEFAULT false;
