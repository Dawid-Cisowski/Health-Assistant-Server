package com.healthassistant.reports.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Aggregated health data snapshot for weekly/monthly reports")
public record RangeReportDataSnapshot(
    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with data in the range")
    Integer daysWithData,

    @JsonProperty("activity")
    @Schema(description = "Aggregated activity metrics")
    RangeActivityData activity,

    @JsonProperty("sleep")
    @Schema(description = "Aggregated sleep metrics")
    RangeSleepData sleep,

    @JsonProperty("nutrition")
    @Schema(description = "Aggregated nutrition metrics")
    RangeNutritionData nutrition,

    @JsonProperty("workouts")
    @Schema(description = "Workout summary for the range")
    RangeWorkoutData workouts,

    @JsonProperty("heartRate")
    @Schema(description = "Heart rate summary for the range")
    RangeHeartRateData heartRate,

    @JsonProperty("dailyBreakdown")
    @Schema(description = "Per-day breakdown of key metrics")
    List<DailyBreakdownEntry> dailyBreakdown
) {
    @Schema(description = "Aggregated activity data for a range")
    public record RangeActivityData(
        @JsonProperty("totalSteps") Integer totalSteps,
        @JsonProperty("averageSteps") Integer averageSteps,
        @JsonProperty("totalActiveMinutes") Integer totalActiveMinutes,
        @JsonProperty("averageActiveMinutes") Integer averageActiveMinutes,
        @JsonProperty("totalActiveCalories") Integer totalActiveCalories,
        @JsonProperty("averageActiveCalories") Integer averageActiveCalories,
        @JsonProperty("totalDistanceMeters") Long totalDistanceMeters
    ) {}

    @Schema(description = "Aggregated sleep data for a range")
    public record RangeSleepData(
        @JsonProperty("totalSleepMinutes") Integer totalSleepMinutes,
        @JsonProperty("averageSleepMinutes") Integer averageSleepMinutes,
        @JsonProperty("daysWithSleep") Integer daysWithSleep
    ) {}

    @Schema(description = "Aggregated nutrition data for a range")
    public record RangeNutritionData(
        @JsonProperty("totalCalories") Integer totalCalories,
        @JsonProperty("averageCalories") Integer averageCalories,
        @JsonProperty("totalProtein") Integer totalProtein,
        @JsonProperty("averageProtein") Integer averageProtein,
        @JsonProperty("totalFat") Integer totalFat,
        @JsonProperty("averageFat") Integer averageFat,
        @JsonProperty("totalCarbs") Integer totalCarbs,
        @JsonProperty("averageCarbs") Integer averageCarbs,
        @JsonProperty("totalMeals") Integer totalMeals,
        @JsonProperty("daysWithData") Integer daysWithData
    ) {}

    @Schema(description = "Workout summary for a range")
    public record RangeWorkoutData(
        @JsonProperty("totalWorkouts") Integer totalWorkouts,
        @JsonProperty("daysWithWorkouts") Integer daysWithWorkouts,
        @JsonProperty("workouts") List<RangeWorkoutEntry> workouts
    ) {}

    @Schema(description = "Individual workout in a range")
    public record RangeWorkoutEntry(
        @JsonProperty("workoutId") String workoutId,
        @JsonProperty("note") String note,
        @JsonProperty("date") LocalDate date
    ) {}

    @Schema(description = "Heart rate summary for a range")
    public record RangeHeartRateData(
        @JsonProperty("averageRestingBpm") Integer averageRestingBpm,
        @JsonProperty("averageDailyBpm") Integer averageDailyBpm,
        @JsonProperty("maxBpmOverall") Integer maxBpmOverall,
        @JsonProperty("daysWithData") Integer daysWithData
    ) {}

    @Schema(description = "Key metrics for a single day")
    public record DailyBreakdownEntry(
        @JsonProperty("date") LocalDate date,
        @JsonProperty("steps") Integer steps,
        @JsonProperty("sleepMinutes") Integer sleepMinutes,
        @JsonProperty("activeMinutes") Integer activeMinutes,
        @JsonProperty("activeCalories") Integer activeCalories,
        @JsonProperty("caloriesConsumed") Integer caloriesConsumed,
        @JsonProperty("proteinConsumed") Integer proteinConsumed,
        @JsonProperty("workoutCount") Integer workoutCount
    ) {}
}
