package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Unique workout identifier from GymRun app", example = "gymrun-2025-11-17-1")
    String workoutId,

    @JsonProperty("performedAt")
    @Schema(description = "When the workout was performed (ISO-8601 UTC)", example = "2025-11-17T18:00:00Z")
    Instant performedAt,

    @JsonProperty("source")
    @Schema(description = "Source of the workout data", example = "GYMRUN_SCREENSHOT")
    String source,

    @JsonProperty("note")
    @Schema(description = "Optional note about the workout", example = "Plecy i biceps", nullable = true)
    String note,

    @JsonProperty("exercises")
    @Schema(description = "List of exercises performed in this workout")
    List<Exercise> exercises
) implements EventPayload {

    @Schema(description = "Single exercise in the workout")
    public record Exercise(
        @JsonProperty("name")
        @Schema(description = "Exercise name", example = "Podciąganie się nachwytem (szeroki rozstaw rąk)")
        String name,

        @JsonProperty("muscleGroup")
        @Schema(description = "Target muscle group", example = "Plecy", nullable = true)
        String muscleGroup,

        @JsonProperty("orderInWorkout")
        @Schema(description = "Order of this exercise in the workout", example = "1")
        int orderInWorkout,

        @JsonProperty("sets")
        @Schema(description = "List of sets performed for this exercise")
        List<ExerciseSet> sets
    ) {}

    @Schema(description = "Single set of an exercise")
    public record ExerciseSet(
        @JsonProperty("setNumber")
        @Schema(description = "Set number", example = "1")
        int setNumber,

        @JsonProperty("weightKg")
        @Schema(description = "Weight used in kilograms", example = "73.0")
        double weightKg,

        @JsonProperty("reps")
        @Schema(description = "Number of repetitions", example = "12")
        int reps,

        @JsonProperty("isWarmup")
        @Schema(description = "Whether this is a warmup set", example = "false")
        boolean isWarmup
    ) {}
}
