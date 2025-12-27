package com.healthassistant.meals.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Meals summary for a date range (week, month, or year)")
public record MealsRangeSummaryResponse(
    @JsonProperty("startDate")
    @Schema(description = "Start date of range", example = "2025-11-01")
    LocalDate startDate,

    @JsonProperty("endDate")
    @Schema(description = "End date of range", example = "2025-11-30")
    LocalDate endDate,

    @JsonProperty("totalMealCount")
    @Schema(description = "Total number of meals in the range", example = "90")
    Integer totalMealCount,

    @JsonProperty("daysWithData")
    @Schema(description = "Number of days with at least one meal", example = "28")
    Integer daysWithData,

    @JsonProperty("totalCaloriesKcal")
    @Schema(description = "Total calories for the entire range", example = "63000")
    Integer totalCaloriesKcal,

    @JsonProperty("totalProteinGrams")
    @Schema(description = "Total protein for the entire range in grams", example = "3600")
    Integer totalProteinGrams,

    @JsonProperty("totalFatGrams")
    @Schema(description = "Total fat for the entire range in grams", example = "2400")
    Integer totalFatGrams,

    @JsonProperty("totalCarbohydratesGrams")
    @Schema(description = "Total carbohydrates for the entire range in grams", example = "6000")
    Integer totalCarbohydratesGrams,

    @JsonProperty("averageCaloriesPerDay")
    @Schema(description = "Average calories per day", example = "2100")
    Integer averageCaloriesPerDay,

    @JsonProperty("averageMealsPerDay")
    @Schema(description = "Average number of meals per day", example = "3")
    Integer averageMealsPerDay,

    @JsonProperty("dayWithMostCalories")
    @Schema(description = "Day with most calories in the range")
    DayExtreme dayWithMostCalories,

    @JsonProperty("dayWithMostMeals")
    @Schema(description = "Day with most meals in the range")
    DayExtremeMeals dayWithMostMeals,

    @JsonProperty("dailyStats")
    @Schema(description = "Daily statistics for each day in the range")
    List<DailyStats> dailyStats
) {
    public record DayExtreme(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-15")
        LocalDate date,

        @JsonProperty("calories")
        @Schema(description = "Calories on this day", example = "2800")
        Integer calories
    ) {}

    public record DayExtremeMeals(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-20")
        LocalDate date,

        @JsonProperty("mealCount")
        @Schema(description = "Number of meals on this day", example = "5")
        Integer mealCount
    ) {}

    public record DailyStats(
        @JsonProperty("date")
        @Schema(description = "Date", example = "2025-11-01")
        LocalDate date,

        @JsonProperty("totalMealCount")
        @Schema(description = "Number of meals for this day", example = "3")
        Integer totalMealCount,

        @JsonProperty("totalCaloriesKcal")
        @Schema(description = "Total calories for this day", example = "2100")
        Integer totalCaloriesKcal,

        @JsonProperty("totalProteinGrams")
        @Schema(description = "Total protein for this day in grams", example = "120")
        Integer totalProteinGrams,

        @JsonProperty("totalFatGrams")
        @Schema(description = "Total fat for this day in grams", example = "80")
        Integer totalFatGrams,

        @JsonProperty("totalCarbohydratesGrams")
        @Schema(description = "Total carbohydrates for this day in grams", example = "200")
        Integer totalCarbohydratesGrams
    ) {}
}
