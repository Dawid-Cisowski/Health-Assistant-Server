package com.healthassistant.workoutimport;

import java.time.Instant;
import java.util.List;

@SuppressWarnings("PMD.UnusedAssignment")
record ExtractedWorkoutData(
        boolean isValid,
        String validationError,
        Instant performedAt,
        String note,
        List<Exercise> exercises,
        double confidence
) {
    ExtractedWorkoutData {
        exercises = exercises != null ? List.copyOf(exercises) : List.of();
    }

    @SuppressWarnings("PMD.UnusedAssignment")
    record Exercise(
            String name,
            String exerciseId,
            double matchConfidence,
            boolean isNewExercise,
            String suggestedId,
            String suggestedPrimaryMuscle,
            String suggestedDescription,
            String muscleGroup,
            int orderInWorkout,
            List<ExerciseSet> sets
    ) {
        public Exercise {
            sets = sets != null ? List.copyOf(sets) : List.of();
        }

        static Exercise matched(String name, String exerciseId, double confidence,
                                String muscleGroup, int orderInWorkout, List<ExerciseSet> sets) {
            return new Exercise(name, exerciseId, confidence, false, null, null, null,
                    muscleGroup, orderInWorkout, sets);
        }

        static Exercise newExercise(String name, String suggestedId, String suggestedPrimaryMuscle,
                                    String suggestedDescription, String muscleGroup,
                                    int orderInWorkout, List<ExerciseSet> sets) {
            return new Exercise(name, null, 0.0, true, suggestedId, suggestedPrimaryMuscle,
                    suggestedDescription, muscleGroup, orderInWorkout, sets);
        }
    }

    record ExerciseSet(
            int setNumber,
            double weightKg,
            int reps,
            boolean isWarmup
    ) {
    }

    static ExtractedWorkoutData valid(
            Instant performedAt, String note, List<Exercise> exercises, double confidence
    ) {
        return new ExtractedWorkoutData(true, null, performedAt, note, exercises, confidence);
    }

    static ExtractedWorkoutData invalid(String error, double confidence) {
        return new ExtractedWorkoutData(false, error, null, null, List.of(), confidence);
    }
}
