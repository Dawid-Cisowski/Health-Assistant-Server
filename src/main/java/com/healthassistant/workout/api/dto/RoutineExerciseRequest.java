package com.healthassistant.workout.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoutineExerciseRequest(
        @NotBlank(message = "exerciseId is required")
        String exerciseId,

        @NotNull(message = "orderIndex is required")
        @Min(value = 1, message = "orderIndex must be at least 1")
        Integer orderIndex,

        @Min(value = 1, message = "defaultSets must be at least 1")
        Integer defaultSets,

        String notes
) {
    public RoutineExerciseRequest {
        if (defaultSets == null) {
            defaultSets = 3;
        }
    }
}
