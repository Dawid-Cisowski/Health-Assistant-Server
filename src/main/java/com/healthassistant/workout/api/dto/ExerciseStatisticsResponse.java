package com.healthassistant.workout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Exercise statistics including summary, progression, and workout history")
public record ExerciseStatisticsResponse(
    @JsonProperty("exerciseId")
    @Schema(description = "Exercise catalog ID", example = "chest_1")
    String exerciseId,

    @JsonProperty("exerciseName")
    @Schema(description = "Exercise display name", example = "Wyciskanie sztangi leżąc (płasko)")
    String exerciseName,

    @JsonProperty("muscleGroup")
    @Schema(description = "Primary muscle group", example = "CHEST")
    String muscleGroup,

    @JsonProperty("description")
    @Schema(description = "Exercise description", example = "Klasyczne ćwiczenie budujące ogólną masę i siłę klatki piersiowej.")
    String description,

    @JsonProperty("summary")
    @Schema(description = "Summary statistics for the exercise")
    ExerciseSummary summary,

    @JsonProperty("history")
    @Schema(description = "Workout history for this exercise, sorted by date descending")
    List<WorkoutHistoryEntry> history
) {
    @Schema(description = "Summary statistics for an exercise")
    public record ExerciseSummary(
        @JsonProperty("personalRecordKg")
        @Schema(description = "Personal record weight in kg", example = "105.0")
        BigDecimal personalRecordKg,

        @JsonProperty("personalRecordDate")
        @Schema(description = "Date when personal record was achieved", example = "2024-12-10")
        LocalDate personalRecordDate,

        @JsonProperty("totalSets")
        @Schema(description = "Total number of sets performed across all workouts", example = "342")
        int totalSets,

        @JsonProperty("totalVolumeKg")
        @Schema(description = "Total volume (weight × reps) across all workouts", example = "154000.0")
        BigDecimal totalVolumeKg,

        @JsonProperty("progressionPercentage")
        @Schema(description = "Progression percentage based on linear regression trend", example = "15.5")
        BigDecimal progressionPercentage
    ) {}

    @Schema(description = "Single workout history entry for an exercise")
    public record WorkoutHistoryEntry(
        @JsonProperty("workoutId")
        @Schema(description = "Unique workout identifier", example = "gymrun-2024-12-24-1")
        String workoutId,

        @JsonProperty("date")
        @Schema(description = "Date when the workout was performed", example = "2024-12-24")
        LocalDate date,

        @JsonProperty("maxWeightKg")
        @Schema(description = "Maximum weight used in this workout", example = "100.0")
        BigDecimal maxWeightKg,

        @JsonProperty("estimated1RM")
        @Schema(description = "Estimated 1 rep max based on best set (Brzycki formula)", example = "120.0")
        BigDecimal estimated1RM,

        @JsonProperty("totalSets")
        @Schema(description = "Number of sets in this workout for this exercise", example = "3")
        int totalSets,

        @JsonProperty("sets")
        @Schema(description = "Individual set details")
        List<SetEntry> sets
    ) {}

    @Schema(description = "Individual set details")
    public record SetEntry(
        @JsonProperty("setNumber")
        @Schema(description = "Set number", example = "1")
        int setNumber,

        @JsonProperty("weightKg")
        @Schema(description = "Weight used in kg", example = "100.0")
        BigDecimal weightKg,

        @JsonProperty("reps")
        @Schema(description = "Number of repetitions", example = "4")
        int reps,

        @JsonProperty("isPr")
        @Schema(description = "Whether this set matches the all-time personal record weight", example = "true")
        boolean isPr
    ) {}
}
