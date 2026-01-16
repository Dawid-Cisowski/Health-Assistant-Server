package com.healthassistant.meals.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Daily energy requirements and macro targets based on Katch-McArdle formula")
public record EnergyRequirementsResponse(
    @JsonProperty("date")
    @Schema(description = "Date for these calculations", example = "2026-01-16")
    LocalDate date,

    @JsonProperty("effectiveLbmKg")
    @Schema(description = "Effective Lean Body Mass (average of last 3 measurements)", example = "57.4")
    BigDecimal effectiveLbmKg,

    @JsonProperty("lbmMeasurementsUsed")
    @Schema(description = "Number of LBM measurements used for averaging (1-3)", example = "3")
    int lbmMeasurementsUsed,

    @JsonProperty("currentWeightKg")
    @Schema(description = "Current body weight from latest measurement", example = "72.6")
    BigDecimal currentWeightKg,

    @JsonProperty("bmrKcal")
    @Schema(description = "Basal Metabolic Rate from Katch-McArdle formula", example = "1610")
    int bmrKcal,

    @JsonProperty("baseKcal")
    @Schema(description = "Base calories (BMR × 1.35 multiplier)", example = "2173")
    int baseKcal,

    @JsonProperty("surplusKcal")
    @Schema(description = "Fixed surplus for anabolism", example = "300")
    int surplusKcal,

    @JsonProperty("steps")
    @Schema(description = "Total steps for the day", example = "8000")
    int steps,

    @JsonProperty("stepIntervals")
    @Schema(description = "Number of step intervals (each 2000 steps above 4000 threshold)", example = "2")
    int stepIntervals,

    @JsonProperty("stepBonusKcal")
    @Schema(description = "Bonus calories from steps (intervals × 90 kcal)", example = "180")
    int stepBonusKcal,

    @JsonProperty("isTrainingDay")
    @Schema(description = "Whether there was a strength training workout", example = "true")
    boolean isTrainingDay,

    @JsonProperty("trainingBonusKcal")
    @Schema(description = "Bonus calories for training day (+250 if training)", example = "250")
    int trainingBonusKcal,

    @JsonProperty("targetCaloriesKcal")
    @Schema(description = "Total target calories for the day", example = "2903")
    int targetCaloriesKcal,

    @JsonProperty("macroTargets")
    @Schema(description = "Target macronutrient distribution")
    MacroTargets macroTargets,

    @JsonProperty("consumed")
    @Schema(description = "Current intake from meals (null if no meal data)")
    ConsumedNutrition consumed,

    @JsonProperty("remaining")
    @Schema(description = "Remaining nutrients to reach targets (null if no meal data, can be negative if targets exceeded)")
    RemainingNutrition remaining
) {
    @Schema(description = "Consumed nutrients from meals today")
    public record ConsumedNutrition(
        @JsonProperty("caloriesKcal")
        @Schema(description = "Calories consumed", example = "1200")
        int caloriesKcal,

        @JsonProperty("proteinGrams")
        @Schema(description = "Protein consumed in grams", example = "80")
        int proteinGrams,

        @JsonProperty("fatGrams")
        @Schema(description = "Fat consumed in grams", example = "30")
        int fatGrams,

        @JsonProperty("carbsGrams")
        @Schema(description = "Carbs consumed in grams", example = "150")
        int carbsGrams
    ) {}

    @Schema(description = "Remaining nutrients to reach targets (values can be negative if targets have been exceeded)")
    public record RemainingNutrition(
        @JsonProperty("caloriesKcal")
        @Schema(description = "Remaining calories (negative if target exceeded)", example = "1703")
        int caloriesKcal,

        @JsonProperty("proteinGrams")
        @Schema(description = "Remaining protein in grams (negative if target exceeded)", example = "81")
        int proteinGrams,

        @JsonProperty("fatGrams")
        @Schema(description = "Remaining fat in grams (negative if target exceeded)", example = "20")
        int fatGrams,

        @JsonProperty("carbsGrams")
        @Schema(description = "Remaining carbs in grams (negative if target exceeded)", example = "306")
        int carbsGrams
    ) {}
}
