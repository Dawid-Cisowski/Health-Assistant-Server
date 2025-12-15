package com.healthassistant.workoutimport.dto;

import java.time.Instant;
import java.util.List;

@SuppressWarnings("PMD.UnusedAssignment")
public record ExtractedWorkoutData(
    boolean isValid,
    String validationError,
    Instant performedAt,
    String note,
    List<Exercise> exercises,
    double confidence
) {
    public ExtractedWorkoutData {
        exercises = exercises != null ? List.copyOf(exercises) : List.of();
    }

    @SuppressWarnings("PMD.UnusedAssignment")
    public record Exercise(
        String name,
        String muscleGroup,
        int orderInWorkout,
        List<ExerciseSet> sets
    ) {
        public Exercise {
            sets = sets != null ? List.copyOf(sets) : List.of();
        }
    }

    public record ExerciseSet(
        int setNumber,
        double weightKg,
        int reps,
        boolean isWarmup
    ) {}

    public static ExtractedWorkoutData valid(
        Instant performedAt, String note, List<Exercise> exercises, double confidence
    ) {
        return new ExtractedWorkoutData(true, null, performedAt, note, exercises, confidence);
    }

    public static ExtractedWorkoutData invalid(String error, double confidence) {
        return new ExtractedWorkoutData(false, error, null, null, List.of(), confidence);
    }
}
