package com.healthassistant.meals.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "Daily meal detail with all meals for dashboard view")
public record MealDailyDetailResponse(
    @JsonProperty("date")
    @Schema(description = "Date for the meal data", example = "2025-11-19")
    LocalDate date,

    @JsonProperty("totalMealCount")
    @Schema(description = "Total number of meals for the day", example = "4")
    Integer totalMealCount,

    @JsonProperty("totalCaloriesKcal")
    @Schema(description = "Total calories for the day", example = "2100")
    Integer totalCaloriesKcal,

    @JsonProperty("totalProteinGrams")
    @Schema(description = "Total protein for the day in grams", example = "120")
    Integer totalProteinGrams,

    @JsonProperty("totalFatGrams")
    @Schema(description = "Total fat for the day in grams", example = "80")
    Integer totalFatGrams,

    @JsonProperty("totalCarbohydratesGrams")
    @Schema(description = "Total carbohydrates for the day in grams", example = "200")
    Integer totalCarbohydratesGrams,

    @JsonProperty("averageCaloriesPerMeal")
    @Schema(description = "Average calories per meal", example = "525")
    Integer averageCaloriesPerMeal,

    @JsonProperty("mealTypeCounts")
    @Schema(description = "Count of meals by type")
    MealTypeCounts mealTypeCounts,

    @JsonProperty("healthRatingCounts")
    @Schema(description = "Count of meals by health rating")
    HealthRatingCounts healthRatingCounts,

    @JsonProperty("firstMealTime")
    @Schema(description = "Time of first meal", example = "2025-11-19T07:30:00Z")
    Instant firstMealTime,

    @JsonProperty("lastMealTime")
    @Schema(description = "Time of last meal", example = "2025-11-19T19:00:00Z")
    Instant lastMealTime,

    @JsonProperty("meals")
    @Schema(description = "Individual meals for the day")
    List<MealDetail> meals
) {
    @Builder
    public record MealDetail(
        @JsonProperty("mealNumber")
        @Schema(description = "Sequential meal number for the day", example = "1")
        Integer mealNumber,

        @JsonProperty("occurredAt")
        @Schema(description = "When the meal occurred", example = "2025-11-19T07:30:00Z")
        Instant occurredAt,

        @JsonProperty("title")
        @Schema(description = "Name/description of the meal", example = "Scrambled eggs with toast")
        String title,

        @JsonProperty("mealType")
        @Schema(description = "Type of meal", example = "BREAKFAST")
        String mealType,

        @JsonProperty("caloriesKcal")
        @Schema(description = "Calories in kcal", example = "450")
        Integer caloriesKcal,

        @JsonProperty("proteinGrams")
        @Schema(description = "Protein in grams", example = "25")
        Integer proteinGrams,

        @JsonProperty("fatGrams")
        @Schema(description = "Fat in grams", example = "20")
        Integer fatGrams,

        @JsonProperty("carbohydratesGrams")
        @Schema(description = "Carbohydrates in grams", example = "35")
        Integer carbohydratesGrams,

        @JsonProperty("healthRating")
        @Schema(description = "Health rating of the meal", example = "HEALTHY")
        String healthRating
    ) {}

    @Builder
    public record MealTypeCounts(
        @JsonProperty("breakfast")
        @Schema(description = "Number of breakfast meals", example = "1")
        Integer breakfast,

        @JsonProperty("brunch")
        @Schema(description = "Number of brunch meals", example = "0")
        Integer brunch,

        @JsonProperty("lunch")
        @Schema(description = "Number of lunch meals", example = "1")
        Integer lunch,

        @JsonProperty("dinner")
        @Schema(description = "Number of dinner meals", example = "1")
        Integer dinner,

        @JsonProperty("snack")
        @Schema(description = "Number of snack meals", example = "1")
        Integer snack,

        @JsonProperty("dessert")
        @Schema(description = "Number of dessert meals", example = "0")
        Integer dessert,

        @JsonProperty("drink")
        @Schema(description = "Number of drink meals", example = "0")
        Integer drink
    ) {}

    @Builder
    public record HealthRatingCounts(
        @JsonProperty("veryHealthy")
        @Schema(description = "Number of very healthy meals", example = "1")
        Integer veryHealthy,

        @JsonProperty("healthy")
        @Schema(description = "Number of healthy meals", example = "2")
        Integer healthy,

        @JsonProperty("neutral")
        @Schema(description = "Number of neutral meals", example = "1")
        Integer neutral,

        @JsonProperty("unhealthy")
        @Schema(description = "Number of unhealthy meals", example = "0")
        Integer unhealthy,

        @JsonProperty("veryUnhealthy")
        @Schema(description = "Number of very unhealthy meals", example = "0")
        Integer veryUnhealthy
    ) {}
}
