package com.healthassistant.workoutimport;

import java.util.List;

record AiWorkoutExtractionResponse(
        boolean isWorkoutScreenshot,
        double confidence,
        String performedAt,
        String note,
        List<AiExercise> exercises,
        String validationError
) {
    record AiExercise(
            String name,
            String exerciseId,
            Double matchConfidence,
            Boolean isNewExercise,
            String suggestedId,
            String suggestedPrimaryMuscle,
            String suggestedDescription,
            String muscleGroup,
            Integer orderInWorkout,
            List<AiExerciseSet> sets
    ) {}

    record AiExerciseSet(
            Integer setNumber,
            Double weightKg,
            Integer reps,
            Boolean isWarmup
    ) {}
}
