-- Fix unique constraint to allow same exercise multiple times in a workout
-- The old constraint (workout_id, exercise_name, set_number) fails when same exercise appears twice

-- Add exercise_order column to track which instance of the exercise this set belongs to
ALTER TABLE workout_set_projections
ADD COLUMN exercise_order INTEGER NOT NULL DEFAULT 1;

-- Drop the old constraint that doesn't account for exercise order
ALTER TABLE workout_set_projections
DROP CONSTRAINT IF EXISTS uq_workout_exercise_set;

-- Create new constraint including exercise_order
-- This allows: workout1, "Bench Press", order=1, set=1 AND workout1, "Bench Press", order=3, set=1
ALTER TABLE workout_set_projections
ADD CONSTRAINT uq_workout_exercise_order_set UNIQUE(workout_id, exercise_order, set_number);

-- Index for efficient queries by exercise
CREATE INDEX IF NOT EXISTS idx_workout_set_exercise_order
ON workout_set_projections(workout_id, exercise_order);
