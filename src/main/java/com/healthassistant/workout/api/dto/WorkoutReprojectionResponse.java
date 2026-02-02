package com.healthassistant.workout.api.dto;

public record WorkoutReprojectionResponse(
        int reprojectedCount,
        int failedCount,
        int totalEventsProcessed
) {}
