package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@Schema(
    description = "Gym workout session with exercises, sets, reps, and weights",
    example = """
        {
          "workoutId": "gymrun-2025-11-17-1",
          "performedAt": "2025-11-17T18:00:00Z",
          "source": "GYMRUN_SCREENSHOT",
          "note": "Plecy i biceps",
          "exercises": [
            {
              "name": "Podciąganie się nachwytem (szeroki rozstaw rąk)",
              "muscleGroup": null,
              "orderInWorkout": 1,
              "sets": [
                { "setNumber": 1, "weightKg": 73.0, "reps": 12, "isWarmup": false },
                { "setNumber": 2, "weightKg": 73.5, "reps": 10, "isWarmup": false }
              ]
            }
          ]
        }
        """
)
public record WorkoutPayload(
    @JsonProperty("workoutId")
    @NotBlank(message = "workoutId is required")
    @Schema(description = "Unique workout identifier from GymRun app", example = "gymrun-2025-11-17-1")
    String workoutId,

    @JsonProperty("performedAt")
    @NotNull(message = "performedAt is required")
    @Schema(description = "When the workout was performed (ISO-8601 UTC)", example = "2025-11-17T18:00:00Z")
    Instant performedAt,

    @JsonProperty("source")
    @Schema(description = "Source of the workout data", example = "GYMRUN_SCREENSHOT")
    String source,

    @JsonProperty("note")
    @Schema(description = "Optional note about the workout", example = "Plecy i biceps", nullable = true)
    String note,

    @JsonProperty("exercises")
    @NotEmpty(message = "exercises cannot be empty")
    @Valid
    @Schema(description = "List of exercises performed in this workout")
    List<Exercise> exercises
) implements EventPayload {

    @Schema(description = "Single exercise in the workout")
    public record Exercise(
        @JsonProperty("name")
        @NotBlank(message = "exercise name is required")
        @Schema(description = "Exercise name", example = "Podciąganie się nachwytem (szeroki rozstaw rąk)")
        String name,

        @JsonProperty("exerciseId")
        @Size(max = 50, message = "exerciseId must not exceed 50 characters")
        @Schema(description = "Catalog exercise ID (FK to exercises table)", example = "back_2", nullable = true)
        String exerciseId,

        @JsonProperty("muscleGroup")
        @Schema(description = "Target muscle group", example = "Plecy", nullable = true)
        String muscleGroup,

        @JsonProperty("orderInWorkout")
        @Schema(description = "Order of this exercise in the workout", example = "1")
        int orderInWorkout,

        @JsonProperty("sets")
        @NotEmpty(message = "sets cannot be empty")
        @Valid
        @Schema(description = "List of sets performed for this exercise")
        List<ExerciseSet> sets
    ) {}

    @Schema(description = "Single set of an exercise")
    public record ExerciseSet(
        @JsonProperty("setNumber")
        @Schema(description = "Set number", example = "1")
        int setNumber,

        @JsonProperty("weightKg")
        @Min(value = 0, message = "must be non-negative")
        @Schema(description = "Weight used in kilograms", example = "73.0")
        double weightKg,

        @JsonProperty("reps")
        @Min(value = 1, message = "must be positive")
        @Schema(description = "Number of repetitions", example = "12")
        int reps,

        @JsonProperty("isWarmup")
        @Schema(description = "Whether this is a warmup set", example = "false")
        boolean isWarmup
    ) {}
}
