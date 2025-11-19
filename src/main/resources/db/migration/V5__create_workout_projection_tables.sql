-- Create workout projection tables for detailed workout analysis and tracking

-- Main workout projection table
CREATE TABLE workout_projections (
    id BIGSERIAL PRIMARY KEY,
    workout_id VARCHAR(255) NOT NULL UNIQUE,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    performed_date DATE NOT NULL,
    source VARCHAR(128) NOT NULL,
    note TEXT,
    total_exercises INTEGER NOT NULL,
    total_sets INTEGER NOT NULL,
    total_volume_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_working_volume_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    device_id VARCHAR(128) NOT NULL,
    event_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Exercise projection table
CREATE TABLE workout_exercise_projections (
    id BIGSERIAL PRIMARY KEY,
    workout_id VARCHAR(255) NOT NULL,
    exercise_name VARCHAR(255) NOT NULL,
    muscle_group VARCHAR(128),
    order_in_workout INTEGER NOT NULL,
    total_sets INTEGER NOT NULL,
    total_volume_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_weight_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exercise_workout FOREIGN KEY (workout_id) REFERENCES workout_projections(workout_id) ON DELETE CASCADE,
    CONSTRAINT uq_workout_exercise_order UNIQUE(workout_id, order_in_workout)
);

-- Set projection table
CREATE TABLE workout_set_projections (
    id BIGSERIAL PRIMARY KEY,
    workout_id VARCHAR(255) NOT NULL,
    exercise_name VARCHAR(255) NOT NULL,
    set_number INTEGER NOT NULL,
    weight_kg DECIMAL(10,2) NOT NULL,
    reps INTEGER NOT NULL,
    is_warmup BOOLEAN NOT NULL DEFAULT false,
    volume_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_set_workout FOREIGN KEY (workout_id) REFERENCES workout_projections(workout_id) ON DELETE CASCADE,
    CONSTRAINT uq_workout_exercise_set UNIQUE(workout_id, exercise_name, set_number)
);

-- Indexes for workout_projections
CREATE INDEX idx_workout_performed_date ON workout_projections(performed_date DESC);
CREATE INDEX idx_workout_performed_at ON workout_projections(performed_at DESC);
CREATE INDEX idx_workout_device_id ON workout_projections(device_id);
CREATE INDEX idx_workout_event_id ON workout_projections(event_id);

-- Indexes for workout_exercise_projections (optimized for progress tracking)
CREATE INDEX idx_exercise_workout_id ON workout_exercise_projections(workout_id);
CREATE INDEX idx_exercise_name ON workout_exercise_projections(exercise_name);
CREATE INDEX idx_exercise_muscle_group ON workout_exercise_projections(muscle_group) WHERE muscle_group IS NOT NULL;
CREATE INDEX idx_exercise_name_workout ON workout_exercise_projections(exercise_name, workout_id);

-- Indexes for workout_set_projections (optimized for progress queries)
CREATE INDEX idx_set_workout_exercise ON workout_set_projections(workout_id, exercise_name);
CREATE INDEX idx_set_exercise_name ON workout_set_projections(exercise_name);

-- Composite index for exercise progress over time (joins with workout_projections)
CREATE INDEX idx_exercise_progress ON workout_exercise_projections(exercise_name, workout_id);

-- Comments for workout_projections
COMMENT ON TABLE workout_projections IS 'Projection of workout events for fast querying and analysis';
COMMENT ON COLUMN workout_projections.workout_id IS 'Unique identifier from WorkoutRecorded event';
COMMENT ON COLUMN workout_projections.performed_at IS 'Exact timestamp when workout was performed';
COMMENT ON COLUMN workout_projections.performed_date IS 'Date of workout (for calendar view and daily grouping)';
COMMENT ON COLUMN workout_projections.source IS 'Source system (e.g., GYMRUN_SCREENSHOT, MANUAL)';
COMMENT ON COLUMN workout_projections.note IS 'User note about the workout (e.g., muscle groups trained)';
COMMENT ON COLUMN workout_projections.total_exercises IS 'Count of distinct exercises in workout';
COMMENT ON COLUMN workout_projections.total_sets IS 'Total number of sets across all exercises';
COMMENT ON COLUMN workout_projections.total_volume_kg IS 'Total volume (sum of weight * reps for all sets)';
COMMENT ON COLUMN workout_projections.total_working_volume_kg IS 'Working volume (excluding warmup sets)';
COMMENT ON COLUMN workout_projections.device_id IS 'Device that recorded the workout';
COMMENT ON COLUMN workout_projections.event_id IS 'Reference to source health_events.event_id';

-- Comments for workout_exercise_projections
COMMENT ON TABLE workout_exercise_projections IS 'Projection of exercises within workouts';
COMMENT ON COLUMN workout_exercise_projections.workout_id IS 'Reference to parent workout';
COMMENT ON COLUMN workout_exercise_projections.exercise_name IS 'Name of the exercise';
COMMENT ON COLUMN workout_exercise_projections.muscle_group IS 'Primary muscle group targeted (nullable)';
COMMENT ON COLUMN workout_exercise_projections.order_in_workout IS 'Order in which exercise was performed (1-based)';
COMMENT ON COLUMN workout_exercise_projections.total_sets IS 'Number of sets for this exercise';
COMMENT ON COLUMN workout_exercise_projections.total_volume_kg IS 'Total volume for this exercise (weight * reps summed)';
COMMENT ON COLUMN workout_exercise_projections.max_weight_kg IS 'Maximum weight used in any set for this exercise';

-- Comments for workout_set_projections
COMMENT ON TABLE workout_set_projections IS 'Projection of individual sets within exercises';
COMMENT ON COLUMN workout_set_projections.workout_id IS 'Reference to parent workout';
COMMENT ON COLUMN workout_set_projections.exercise_name IS 'Name of the exercise this set belongs to';
COMMENT ON COLUMN workout_set_projections.set_number IS 'Set number within the exercise (1-based)';
COMMENT ON COLUMN workout_set_projections.weight_kg IS 'Weight used in kilograms (0 for bodyweight exercises)';
COMMENT ON COLUMN workout_set_projections.reps IS 'Number of repetitions performed';
COMMENT ON COLUMN workout_set_projections.is_warmup IS 'Whether this set is a warmup set';
COMMENT ON COLUMN workout_set_projections.volume_kg IS 'Pre-calculated volume (weight * reps)';
