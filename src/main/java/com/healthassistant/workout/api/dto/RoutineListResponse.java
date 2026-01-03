package com.healthassistant.workout.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RoutineListResponse(
        UUID id,
        String name,
        String description,
        String colorTheme,
        Instant createdAt,
        int exerciseCount
) {}
