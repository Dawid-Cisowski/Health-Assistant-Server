package com.healthassistant.workout.api.dto;

import java.util.List;

public record ExerciseDefinition(
        String id,
        String name,
        String description,
        String primaryMuscle,
        List<String> muscles
) {}
