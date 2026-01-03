package com.healthassistant.workout.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoutineRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        String description,

        @Size(max = 50, message = "colorTheme must be at most 50 characters")
        String colorTheme,

        @NotEmpty(message = "exercises list cannot be empty")
        @Valid
        List<RoutineExerciseRequest> exercises
) {
    public RoutineRequest {
        if (colorTheme == null || colorTheme.isBlank()) {
            colorTheme = "bg-indigo-500";
        }
    }
}
