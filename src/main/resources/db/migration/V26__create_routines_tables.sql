-- Workout routines (training plan templates)
CREATE TABLE routines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    color_theme VARCHAR(50) DEFAULT 'bg-indigo-500',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 0
);

-- Exercises in a routine with order
CREATE TABLE routine_exercises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routine_id UUID NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    exercise_id VARCHAR(50) NOT NULL REFERENCES exercises(id),
    order_index INTEGER NOT NULL,
    default_sets INTEGER DEFAULT 3,
    notes TEXT
);

-- Indexes
CREATE INDEX idx_routines_device_id ON routines(device_id);
CREATE INDEX idx_routine_exercises_routine_id ON routine_exercises(routine_id);
CREATE INDEX idx_routine_exercises_order ON routine_exercises(routine_id, order_index);
