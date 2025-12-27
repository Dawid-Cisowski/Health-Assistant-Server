package com.healthassistant.workout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Detailed workout information")
public record WorkoutDetailResponse(
    @JsonProperty("workoutId")
    @Schema(description = "Unique workout identifier", example = "gymrun-2025-11-19-1")
    String workoutId,

    @JsonProperty("performedAt")
    @Schema(description = "When the workout was performed (ISO-8601 UTC)", example = "2025-11-19T18:00:00Z")
    Instant performedAt,

    @JsonProperty("performedDate")
    @Schema(description = "Date of the workout", example = "2025-11-19")
    LocalDate performedDate,

    @JsonProperty("source")
    @Schema(description = "Source of the workout data", example = "GYMRUN_SCREENSHOT")
    String source,

    @JsonProperty("note")
    @Schema(description = "Workout note/description", example = "Plecy i biceps", nullable = true)
    String note,

    @JsonProperty("totalExercises")
    @Schema(description = "Total number of exercises", example = "5")
    Integer totalExercises,

    @JsonProperty("totalSets")
    @Schema(description = "Total number of sets", example = "15")
    Integer totalSets,

    @JsonProperty("totalVolumeKg")
    @Schema(description = "Total volume (weight × reps) in kg", example = "3151.5")
    BigDecimal totalVolumeKg,

    @JsonProperty("totalWorkingVolumeKg")
    @Schema(description = "Working volume excluding warmup sets", example = "2950.0")
    BigDecimal totalWorkingVolumeKg,

    @JsonProperty("exercises")
    @Schema(description = "List of exercises in the workout")
    List<ExerciseDetail> exercises
) {
    @Schema(description = "Exercise details")
    public record ExerciseDetail(
        @JsonProperty("exerciseName")
        @Schema(description = "Exercise name", example = "Podciąganie się nachwytem")
        String exerciseName,

        @JsonProperty("muscleGroup")
        @Schema(description = "Target muscle group", example = "Plecy", nullable = true)
        String muscleGroup,

        @JsonProperty("orderInWorkout")
        @Schema(description = "Order in workout", example = "1")
        Integer orderInWorkout,

        @JsonProperty("totalSets")
        @Schema(description = "Number of sets for this exercise", example = "3")
        Integer totalSets,

        @JsonProperty("totalVolumeKg")
        @Schema(description = "Total volume for this exercise", example = "876.0")
        BigDecimal totalVolumeKg,

        @JsonProperty("maxWeightKg")
        @Schema(description = "Maximum weight used", example = "73.0")
        BigDecimal maxWeightKg,

        @JsonProperty("sets")
        @Schema(description = "List of sets for this exercise")
        List<SetDetail> sets
    ) {}

    @Schema(description = "Set details")
    public record SetDetail(
        @JsonProperty("setNumber")
        @Schema(description = "Set number", example = "1")
        Integer setNumber,

        @JsonProperty("weightKg")
        @Schema(description = "Weight used in kg", example = "73.0")
        BigDecimal weightKg,

        @JsonProperty("reps")
        @Schema(description = "Number of repetitions", example = "12")
        Integer reps,

        @JsonProperty("isWarmup")
        @Schema(description = "Whether this is a warmup set", example = "false")
        Boolean isWarmup,

        @JsonProperty("volumeKg")
        @Schema(description = "Volume for this set (weight × reps)", example = "876.0")
        BigDecimal volumeKg
    ) {}
}
