package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Raw health data snapshot for a report period")
public record ReportDataSnapshot(
    @JsonProperty("activity")
    @Schema(description = "Activity metrics")
    ActivityData activity,

    @JsonProperty("sleep")
    @Schema(description = "Sleep metrics")
    SleepData sleep,

    @JsonProperty("nutrition")
    @Schema(description = "Nutrition metrics")
    NutritionData nutrition,

    @JsonProperty("workouts")
    @Schema(description = "Workouts performed")
    List<WorkoutData> workouts,

    @JsonProperty("heartRate")
    @Schema(description = "Heart rate metrics")
    HeartRateData heartRate
) {
    @Schema(description = "Activity data for daily report")
    public record ActivityData(
        @JsonProperty("steps") Integer steps,
        @JsonProperty("activeMinutes") Integer activeMinutes,
        @JsonProperty("activeCalories") Integer activeCalories,
        @JsonProperty("distanceMeters") Long distanceMeters
    ) {}

    @Schema(description = "Sleep data for daily report")
    public record SleepData(
        @JsonProperty("totalMinutes") Integer totalMinutes,
        @JsonProperty("sessions") List<SleepSession> sessions
    ) {}

    @Schema(description = "Single sleep session")
    public record SleepSession(
        @JsonProperty("start") Instant start,
        @JsonProperty("end") Instant end,
        @JsonProperty("totalMinutes") Integer totalMinutes
    ) {}

    @Schema(description = "Nutrition data for report")
    public record NutritionData(
        @JsonProperty("totalCalories") Integer totalCalories,
        @JsonProperty("totalProtein") Integer totalProtein,
        @JsonProperty("totalFat") Integer totalFat,
        @JsonProperty("totalCarbs") Integer totalCarbs,
        @JsonProperty("mealCount") Integer mealCount,
        @JsonProperty("healthyMealCount") Integer healthyMealCount,
        @JsonProperty("target") NutritionTarget target
    ) {}

    @Schema(description = "Nutrition targets from calculator")
    public record NutritionTarget(
        @JsonProperty("caloriesKcal") int caloriesKcal,
        @JsonProperty("proteinGrams") int proteinGrams,
        @JsonProperty("fatGrams") int fatGrams,
        @JsonProperty("carbsGrams") int carbsGrams
    ) {}

    @Schema(description = "Workout summary entry")
    public record WorkoutData(
        @JsonProperty("workoutId") String workoutId,
        @JsonProperty("note") String note,
        @JsonProperty("performedAt") Instant performedAt
    ) {}

    @Schema(description = "Heart rate data")
    public record HeartRateData(
        @JsonProperty("restingBpm") Integer restingBpm,
        @JsonProperty("avgBpm") Integer avgBpm,
        @JsonProperty("maxBpm") Integer maxBpm
    ) {}
}
