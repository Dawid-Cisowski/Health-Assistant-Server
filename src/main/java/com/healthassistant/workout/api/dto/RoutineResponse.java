package com.healthassistant.workout.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoutineResponse(
        UUID id,
        String name,
        String description,
        String colorTheme,
        Instant createdAt,
        Instant updatedAt,
        List<RoutineExerciseResponse> exercises
) {}
