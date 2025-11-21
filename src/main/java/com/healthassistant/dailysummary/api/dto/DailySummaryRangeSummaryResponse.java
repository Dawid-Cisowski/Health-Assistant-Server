package com.healthassistant.dailysummary.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "Daily summary for a date range (week, month, or year)")
public record DailySummaryRangeSummaryResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of range", example = "2025-11-01")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of range", example = "2025-11-30")
    LocalDate endDate,

    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with summary data", example = "28")
    Integer daysWithData,

    @JsonProperty("activity")
    @Schema(description = "Aggregated activity metrics for the range")
    ActivitySummary activity,

    @JsonProperty("sleep")
    @Schema(description = "Aggregated sleep metrics for the range")
    SleepSummary sleep,

    @JsonProperty("heart")
    @Schema(description = "Heart rate statistics for the range")
    HeartSummary heart,

    @JsonProperty("nutrition")
    @Schema(description = "Aggregated nutrition metrics for the range")
    NutritionSummary nutrition,

    @JsonProperty("workouts")
    @Schema(description = "Workout statistics for the range")
    WorkoutSummary workouts,

    @JsonProperty("dailyStats")
    @Schema(description = "Daily statistics for each day in the range")
    List<DailySummaryResponse> dailyStats
) {
    @Builder
    public record ActivitySummary(
        @JsonProperty("totalSteps")
        @Schema(description = "Total steps for the entire range", example = "324500")
        Integer totalSteps,

        @JsonProperty("averageSteps")
        @Schema(description = "Average steps per day", example = "10817")
        Integer averageSteps,

        @JsonProperty("totalActiveMinutes")
        @Schema(description = "Total active minutes", example = "2100")
        Integer totalActiveMinutes,

        @JsonProperty("averageActiveMinutes")
        @Schema(description = "Average active minutes per day", example = "70")
        Integer averageActiveMinutes,

        @JsonProperty("totalActiveCalories")
        @Schema(description = "Total active calories burned", example = "15000")
        Integer totalActiveCalories,

        @JsonProperty("averageActiveCalories")
        @Schema(description = "Average active calories per day", example = "500")
        Integer averageActiveCalories,

        @JsonProperty("totalDistanceMeters")
        @Schema(description = "Total distance in meters", example = "210000")
        Long totalDistanceMeters,

        @JsonProperty("averageDistanceMeters")
        @Schema(description = "Average distance per day in meters", example = "7000")
        Long averageDistanceMeters
    ) {}

    @Builder
    public record SleepSummary(
        @JsonProperty("totalSleepMinutes")
        @Schema(description = "Total sleep minutes for the entire range", example = "14400")
        Integer totalSleepMinutes,

        @JsonProperty("averageSleepMinutes")
        @Schema(description = "Average sleep minutes per day", example = "480")
        Integer averageSleepMinutes,

        @JsonProperty("daysWithSleep")
        @Schema(description = "Number of days with at least one sleep session", example = "28")
        Integer daysWithSleep
    ) {}

    @Builder
    public record HeartSummary(
        @JsonProperty("averageRestingBpm")
        @Schema(description = "Average resting heart rate in BPM", example = "62")
        Integer averageRestingBpm,

        @JsonProperty("averageDailyBpm")
        @Schema(description = "Average daily heart rate in BPM", example = "74")
        Integer averageDailyBpm,

        @JsonProperty("maxBpmOverall")
        @Schema(description = "Maximum heart rate observed in the range", example = "165")
        Integer maxBpmOverall,

        @JsonProperty("daysWithData")
        @Schema(description = "Number of days with heart rate data", example = "28")
        Integer daysWithData
    ) {}

    @Builder
    public record NutritionSummary(
        @JsonProperty("totalCalories")
        @Schema(description = "Total calories consumed", example = "67200")
        Integer totalCalories,

        @JsonProperty("averageCalories")
        @Schema(description = "Average calories per day", example = "2400")
        Integer averageCalories,

        @JsonProperty("totalProtein")
        @Schema(description = "Total protein consumed in grams", example = "3360")
        Integer totalProtein,

        @JsonProperty("averageProtein")
        @Schema(description = "Average protein per day in grams", example = "120")
        Integer averageProtein,

        @JsonProperty("totalFat")
        @Schema(description = "Total fat consumed in grams", example = "2240")
        Integer totalFat,

        @JsonProperty("averageFat")
        @Schema(description = "Average fat per day in grams", example = "80")
        Integer averageFat,

        @JsonProperty("totalCarbs")
        @Schema(description = "Total carbohydrates consumed in grams", example = "7000")
        Integer totalCarbs,

        @JsonProperty("averageCarbs")
        @Schema(description = "Average carbohydrates per day in grams", example = "250")
        Integer averageCarbs,

        @JsonProperty("totalMeals")
        @Schema(description = "Total number of meals", example = "140")
        Integer totalMeals,

        @JsonProperty("averageMealsPerDay")
        @Schema(description = "Average meals per day", example = "5")
        Integer averageMealsPerDay,

        @JsonProperty("daysWithData")
        @Schema(description = "Number of days with meal data", example = "28")
        Integer daysWithData
    ) {}

    @Builder
    public record WorkoutSummary(
        @JsonProperty("totalWorkouts")
        @Schema(description = "Total number of gym workouts", example = "12")
        Integer totalWorkouts,

        @JsonProperty("daysWithWorkouts")
        @Schema(description = "Number of days with at least one workout", example = "12")
        Integer daysWithWorkouts,

        @JsonProperty("workoutList")
        @Schema(description = "List of all workouts performed in the range")
        List<WorkoutInfo> workoutList
    ) {
        @Builder
        public record WorkoutInfo(
            @JsonProperty("workoutId")
            @Schema(description = "Workout identifier", example = "gymrun-2025-11-19-1")
            String workoutId,

            @JsonProperty("note")
            @Schema(description = "Workout name/note", example = "Plecy i biceps")
            String note,

            @JsonProperty("date")
            @Schema(description = "Date when the workout was performed", example = "2025-11-19")
            LocalDate date
        ) {}
    }
}
