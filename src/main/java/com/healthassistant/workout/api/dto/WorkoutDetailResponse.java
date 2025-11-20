package com.healthassistant.workout.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed workout information")
public class WorkoutDetailResponse {

    @JsonProperty("workoutId")
    @Schema(description = "Unique workout identifier", example = "gymrun-2025-11-19-1")
    private String workoutId;

    @JsonProperty("performedAt")
    @Schema(description = "When the workout was performed (ISO-8601 UTC)", example = "2025-11-19T18:00:00Z")
    private Instant performedAt;

    @JsonProperty("performedDate")
    @Schema(description = "Date of the workout", example = "2025-11-19")
    private LocalDate performedDate;

    @JsonProperty("source")
    @Schema(description = "Source of the workout data", example = "GYMRUN_SCREENSHOT")
    private String source;

    @JsonProperty("note")
    @Schema(description = "Workout note/description", example = "Plecy i biceps", nullable = true)
    private String note;

    @JsonProperty("totalExercises")
    @Schema(description = "Total number of exercises", example = "5")
    private Integer totalExercises;

    @JsonProperty("totalSets")
    @Schema(description = "Total number of sets", example = "15")
    private Integer totalSets;

    @JsonProperty("totalVolumeKg")
    @Schema(description = "Total volume (weight × reps) in kg", example = "3151.5")
    private BigDecimal totalVolumeKg;

    @JsonProperty("totalWorkingVolumeKg")
    @Schema(description = "Working volume excluding warmup sets", example = "2950.0")
    private BigDecimal totalWorkingVolumeKg;

    @JsonProperty("exercises")
    @Schema(description = "List of exercises in the workout")
    private List<ExerciseDetail> exercises;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Exercise details")
    public static class ExerciseDetail {
        @JsonProperty("exerciseName")
        @Schema(description = "Exercise name", example = "Podciąganie się nachwytem")
        private String exerciseName;

        @JsonProperty("muscleGroup")
        @Schema(description = "Target muscle group", example = "Plecy", nullable = true)
        private String muscleGroup;

        @JsonProperty("orderInWorkout")
        @Schema(description = "Order in workout", example = "1")
        private Integer orderInWorkout;

        @JsonProperty("totalSets")
        @Schema(description = "Number of sets for this exercise", example = "3")
        private Integer totalSets;

        @JsonProperty("totalVolumeKg")
        @Schema(description = "Total volume for this exercise", example = "876.0")
        private BigDecimal totalVolumeKg;

        @JsonProperty("maxWeightKg")
        @Schema(description = "Maximum weight used", example = "73.0")
        private BigDecimal maxWeightKg;

        @JsonProperty("sets")
        @Schema(description = "List of sets for this exercise")
        private List<SetDetail> sets;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Set details")
    public static class SetDetail {
        @JsonProperty("setNumber")
        @Schema(description = "Set number", example = "1")
        private Integer setNumber;

        @JsonProperty("weightKg")
        @Schema(description = "Weight used in kg", example = "73.0")
        private BigDecimal weightKg;

        @JsonProperty("reps")
        @Schema(description = "Number of repetitions", example = "12")
        private Integer reps;

        @JsonProperty("isWarmup")
        @Schema(description = "Whether this is a warmup set", example = "false")
        private Boolean isWarmup;

        @JsonProperty("volumeKg")
        @Schema(description = "Volume for this set (weight × reps)", example = "876.0")
        private BigDecimal volumeKg;
    }
}
