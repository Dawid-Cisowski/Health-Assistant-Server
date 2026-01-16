package com.healthassistant.workout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

@Schema(description = "Request to update an existing workout")
public record UpdateWorkoutRequest(
    @NotNull(message = "performedAt is required")
    @JsonProperty("performedAt")
    @Schema(description = "When the workout was performed (ISO-8601 UTC). " +
            "Can be up to 30 days in the past, cannot be in the future.",
            example = "2025-11-17T18:00:00Z")
    Instant performedAt,

    @Nullable
    @Size(max = 100, message = "source must not exceed 100 characters")
    @JsonProperty("source")
    @Schema(description = "Source of the workout data", example = "GYMRUN_SCREENSHOT", nullable = true)
    String source,

    @Nullable
    @Size(max = 500, message = "note must not exceed 500 characters")
    @JsonProperty("note")
    @Schema(description = "Optional note about the workout", example = "Back and biceps day", nullable = true)
    String note,

    @NotEmpty(message = "exercises cannot be empty")
    @Size(max = 50, message = "workout cannot have more than 50 exercises")
    @Valid
    @JsonProperty("exercises")
    @Schema(description = "List of exercises performed in this workout")
    List<ExerciseRequest> exercises
) {

    @Schema(description = "Exercise data for update request")
    public record ExerciseRequest(
        @NotBlank(message = "exercise name is required")
        @Size(max = 200, message = "exercise name must not exceed 200 characters")
        @JsonProperty("name")
        @Schema(description = "Exercise name", example = "Bench Press")
        String name,

        @Nullable
        @Size(max = 50, message = "exerciseId must not exceed 50 characters")
        @JsonProperty("exerciseId")
        @Schema(description = "Catalog exercise ID", example = "chest_1", nullable = true)
        String exerciseId,

        @Nullable
        @Size(max = 50, message = "muscleGroup must not exceed 50 characters")
        @JsonProperty("muscleGroup")
        @Schema(description = "Target muscle group", example = "Chest", nullable = true)
        String muscleGroup,

        @JsonProperty("orderInWorkout")
        @Schema(description = "Order of this exercise in the workout", example = "1")
        int orderInWorkout,

        @NotEmpty(message = "sets cannot be empty")
        @Size(max = 100, message = "exercise cannot have more than 100 sets")
        @Valid
        @JsonProperty("sets")
        @Schema(description = "List of sets performed for this exercise")
        List<SetRequest> sets
    ) {}

    @Schema(description = "Set data for update request")
    public record SetRequest(
        @JsonProperty("setNumber")
        @Schema(description = "Set number", example = "1")
        int setNumber,

        @Min(value = 0, message = "weightKg must be non-negative")
        @Max(value = 1000, message = "weightKg must not exceed 1000 kg")
        @JsonProperty("weightKg")
        @Schema(description = "Weight used in kilograms (max 1000)", example = "80.0")
        double weightKg,

        @Min(value = 1, message = "reps must be positive")
        @Max(value = 1000, message = "reps must not exceed 1000")
        @JsonProperty("reps")
        @Schema(description = "Number of repetitions (max 1000)", example = "10")
        int reps,

        @JsonProperty("isWarmup")
        @Schema(description = "Whether this is a warmup set", example = "false")
        boolean isWarmup
    ) {}
}
