package com.healthassistant.workout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after creating or updating a workout")
public record WorkoutMutationResponse(
    @JsonProperty("eventId")
    @Schema(description = "Unique event identifier", example = "evt_abc123")
    String eventId,

    @JsonProperty("workoutId")
    @Schema(description = "Unique workout identifier", example = "gymrun-2025-11-17-1")
    String workoutId,

    @JsonProperty("performedAt")
    @Schema(description = "When the workout was performed (ISO-8601 UTC)", example = "2025-11-17T18:00:00Z")
    Instant performedAt,

    @JsonProperty("totalExercises")
    @Schema(description = "Total number of exercises in the workout", example = "5")
    int totalExercises,

    @JsonProperty("totalSets")
    @Schema(description = "Total number of sets across all exercises", example = "20")
    int totalSets
) {}
