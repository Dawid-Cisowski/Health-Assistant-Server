package com.healthassistant.dailysummary.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Daily health summary")
public record DailySummaryResponse(
    @JsonProperty("date")
    @Schema(description = "Date for which the summary is generated (YYYY-MM-DD)", example = "2025-11-12")
    LocalDate date,

    @JsonProperty("activity")
    @Schema(description = "Daily activity metrics")
    Activity activity,

    @JsonProperty("exercises")
    @Schema(description = "List of exercises performed during the day")
    List<Exercise> exercises,

    @JsonProperty("workouts")
    @Schema(description = "List of gym workouts performed during the day")
    List<Workout> workouts,

    @JsonProperty("sleep")
    @Schema(description = "Sleep sessions during the day (including naps)")
    List<Sleep> sleep,

    @JsonProperty("heart")
    @Schema(description = "Heart rate metrics")
    Heart heart,

    @JsonProperty("nutrition")
    @Schema(description = "Daily nutrition summary")
    Nutrition nutrition,

    @JsonProperty("meals")
    @Schema(description = "List of meals consumed during the day")
    List<Meal> meals
) {
    @Schema(description = "Daily activity metrics")
    public record Activity(
        @JsonProperty("steps")
        @Schema(description = "Total steps", example = "15631", nullable = true)
        Integer steps,

        @JsonProperty("activeMinutes")
        @Schema(description = "Total active minutes", example = "87", nullable = true)
        Integer activeMinutes,

        @JsonProperty("activeCalories")
        @Schema(description = "Total active calories burned", example = "560", nullable = true)
        Integer activeCalories,

        @JsonProperty("distanceMeters")
        @Schema(description = "Total distance in meters", example = "7100", nullable = true)
        Long distanceMeters
    ) {}

    @Schema(description = "Exercise session")
    public record Exercise(
        @JsonProperty("type")
        @Schema(description = "Exercise type (e.g., WALK, running, cycling)", example = "WALK")
        String type,

        @JsonProperty("start")
        @Schema(description = "Exercise start time (ISO-8601 UTC)", example = "2025-11-12T17:20:00Z")
        Instant start,

        @JsonProperty("end")
        @Schema(description = "Exercise end time (ISO-8601 UTC)", example = "2025-11-12T17:55:00Z")
        Instant end,

        @JsonProperty("durationMinutes")
        @Schema(description = "Exercise duration in minutes", example = "35", nullable = true)
        Integer durationMinutes,

        @JsonProperty("distanceMeters")
        @Schema(description = "Distance covered in meters", example = "5200", nullable = true)
        Long distanceMeters,

        @JsonProperty("steps")
        @Schema(description = "Total steps during exercise", example = "8000", nullable = true)
        Integer steps,

        @JsonProperty("avgHr")
        @Schema(description = "Average heart rate in BPM", example = "141", nullable = true)
        Integer avgHr,

        @JsonProperty("energyKcal")
        @Schema(description = "Calories burned during exercise", example = "320", nullable = true)
        Integer energyKcal
    ) {}

    @Schema(description = "Gym workout session reference")
    public record Workout(
        @JsonProperty("workoutId")
        @Schema(description = "Unique workout identifier", example = "gymrun-2025-11-19-1")
        String workoutId,

        @JsonProperty("note")
        @Schema(description = "Workout note/description", example = "Plecy i biceps", nullable = true)
        String note,

        @JsonProperty("performedAt")
        @Schema(description = "Time when workout was performed (ISO-8601 UTC)", example = "2025-11-19T10:30:00Z", nullable = true)
        Instant performedAt
    ) {}

    @Schema(description = "Sleep session information")
    public record Sleep(
        @JsonProperty("start")
        @Schema(description = "Sleep start time (ISO-8601 UTC)", example = "2025-11-11T23:26:00Z", nullable = true)
        Instant start,

        @JsonProperty("end")
        @Schema(description = "Sleep end time (ISO-8601 UTC)", example = "2025-11-12T06:00:00Z", nullable = true)
        Instant end,

        @JsonProperty("totalMinutes")
        @Schema(description = "Total sleep duration in minutes", example = "394", nullable = true)
        Integer totalMinutes
    ) {}

    @Schema(description = "Heart rate metrics")
    public record Heart(
        @JsonProperty("restingBpm")
        @Schema(description = "Resting heart rate in BPM", example = "62", nullable = true)
        Integer restingBpm,

        @JsonProperty("avgBpm")
        @Schema(description = "Average heart rate in BPM", example = "74", nullable = true)
        Integer avgBpm,

        @JsonProperty("maxBpm")
        @Schema(description = "Maximum heart rate in BPM", example = "138", nullable = true)
        Integer maxBpm
    ) {}

    @Schema(description = "Daily nutrition summary")
    public record Nutrition(
        @JsonProperty("totalCalories")
        @Schema(description = "Total calories consumed in kcal", example = "2400", nullable = true)
        Integer totalCalories,

        @JsonProperty("totalProtein")
        @Schema(description = "Total protein consumed in grams", example = "120", nullable = true)
        Integer totalProtein,

        @JsonProperty("totalFat")
        @Schema(description = "Total fat consumed in grams", example = "80", nullable = true)
        Integer totalFat,

        @JsonProperty("totalCarbs")
        @Schema(description = "Total carbohydrates consumed in grams", example = "250", nullable = true)
        Integer totalCarbs,

        @JsonProperty("mealCount")
        @Schema(description = "Number of meals consumed", example = "5", nullable = true)
        Integer mealCount
    ) {}

    @Schema(description = "Meal information")
    public record Meal(
        @JsonProperty("title")
        @Schema(description = "Meal title/name", example = "Grilled chicken with rice", nullable = true)
        String title,

        @JsonProperty("mealType")
        @Schema(description = "Type of meal", example = "LUNCH", nullable = true)
        String mealType,

        @JsonProperty("caloriesKcal")
        @Schema(description = "Calories in kcal", example = "650", nullable = true)
        Integer caloriesKcal,

        @JsonProperty("proteinGrams")
        @Schema(description = "Protein in grams", example = "45", nullable = true)
        Integer proteinGrams,

        @JsonProperty("fatGrams")
        @Schema(description = "Fat in grams", example = "20", nullable = true)
        Integer fatGrams,

        @JsonProperty("carbohydratesGrams")
        @Schema(description = "Carbohydrates in grams", example = "60", nullable = true)
        Integer carbohydratesGrams,

        @JsonProperty("healthRating")
        @Schema(description = "Health rating of the meal", example = "HEALTHY", nullable = true)
        String healthRating,

        @JsonProperty("occurredAt")
        @Schema(description = "Time when the meal was consumed (ISO-8601 UTC)", example = "2025-11-12T12:30:00Z", nullable = true)
        Instant occurredAt
    ) {}
}
