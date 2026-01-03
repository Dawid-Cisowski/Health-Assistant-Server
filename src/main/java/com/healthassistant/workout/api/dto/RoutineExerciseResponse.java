package com.healthassistant.workout.api.dto;

import java.util.UUID;

public record RoutineExerciseResponse(
        UUID id,
        String exerciseId,
        String exerciseName,
        Integer orderIndex,
        Integer defaultSets,
        String notes
) {}
