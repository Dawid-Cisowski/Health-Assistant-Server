-- Fix unique constraint to allow same exercise multiple times in a workout
-- The old constraint (workout_id, exercise_name, set_number) fails when same exercise appears twice

-- Step 1: Add exercise_order column as NULLABLE (not with DEFAULT 1!)
ALTER TABLE workout_set_projections
ADD COLUMN exercise_order INTEGER;

-- Step 2: Update existing data by joining with workout_exercise_projections
-- to get the correct order_in_workout value for each set
WITH set_exercise_mapping AS (
    SELECT
        s.id AS set_id,
        s.workout_id,
        s.exercise_name,
        -- Count how many times set_number=1 has appeared for this exercise
        -- This identifies which exercise instance this set belongs to
        COALESCE(
            SUM(CASE WHEN s.set_number = 1 THEN 1 ELSE 0 END) OVER (
                PARTITION BY s.workout_id, s.exercise_name
                ORDER BY s.id
                ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
            ),
            1
        ) AS exercise_instance_num
    FROM workout_set_projections s
),
exercise_instances AS (
    SELECT
        workout_id,
        exercise_name,
        order_in_workout,
        ROW_NUMBER() OVER (
            PARTITION BY workout_id, exercise_name
            ORDER BY order_in_workout
        ) AS instance_num
    FROM workout_exercise_projections
),
final_order AS (
    SELECT
        sem.set_id,
        COALESCE(ei.order_in_workout, sem.exercise_instance_num) AS exercise_order
    FROM set_exercise_mapping sem
    LEFT JOIN exercise_instances ei
        ON sem.workout_id = ei.workout_id
        AND sem.exercise_name = ei.exercise_name
        AND sem.exercise_instance_num = ei.instance_num
)
UPDATE workout_set_projections s
SET exercise_order = fo.exercise_order
FROM final_order fo
WHERE s.id = fo.set_id;

-- Step 3: Safety net - set default for any remaining NULL values
UPDATE workout_set_projections
SET exercise_order = 1
WHERE exercise_order IS NULL;

-- Step 4: Make the column NOT NULL after data is populated
ALTER TABLE workout_set_projections
ALTER COLUMN exercise_order SET NOT NULL;

-- Step 5: Drop the old constraint
ALTER TABLE workout_set_projections
DROP CONSTRAINT IF EXISTS uq_workout_exercise_set;

-- Step 6: Create the new unique constraint
ALTER TABLE workout_set_projections
ADD CONSTRAINT uq_workout_exercise_order_set UNIQUE(workout_id, exercise_order, set_number);

-- Step 7: Index for efficient queries
CREATE INDEX IF NOT EXISTS idx_workout_set_exercise_order
ON workout_set_projections(workout_id, exercise_order);
