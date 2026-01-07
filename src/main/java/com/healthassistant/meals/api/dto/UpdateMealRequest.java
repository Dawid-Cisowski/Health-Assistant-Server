package com.healthassistant.meals.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "Request to update an existing meal")
public record UpdateMealRequest(
    @NotBlank(message = "title is required")
    @Size(max = 500, message = "title must not exceed 500 characters")
    @JsonProperty("title")
    @Schema(description = "Name/description of the meal", example = "Scrambled eggs with toast")
    String title,

    @NotNull(message = "mealType is required")
    @JsonProperty("mealType")
    @Schema(description = "Type of meal", example = "BREAKFAST")
    MealType mealType,

    @NotNull(message = "caloriesKcal is required")
    @Min(value = 0, message = "caloriesKcal must be non-negative")
    @JsonProperty("caloriesKcal")
    @Schema(description = "Calories in kcal", example = "450")
    Integer caloriesKcal,

    @NotNull(message = "proteinGrams is required")
    @Min(value = 0, message = "proteinGrams must be non-negative")
    @JsonProperty("proteinGrams")
    @Schema(description = "Protein in grams", example = "25")
    Integer proteinGrams,

    @NotNull(message = "fatGrams is required")
    @Min(value = 0, message = "fatGrams must be non-negative")
    @JsonProperty("fatGrams")
    @Schema(description = "Fat in grams", example = "20")
    Integer fatGrams,

    @NotNull(message = "carbohydratesGrams is required")
    @Min(value = 0, message = "carbohydratesGrams must be non-negative")
    @JsonProperty("carbohydratesGrams")
    @Schema(description = "Carbohydrates in grams", example = "35")
    Integer carbohydratesGrams,

    @NotNull(message = "healthRating is required")
    @JsonProperty("healthRating")
    @Schema(description = "Health rating of the meal", example = "HEALTHY")
    HealthRating healthRating,

    @Nullable
    @JsonProperty("occurredAt")
    @Schema(description = "When the meal occurred. If not provided, keeps the original timestamp. " +
            "Can be up to 30 days in the past, cannot be in the future.",
            example = "2025-11-19T07:30:00Z")
    Instant occurredAt
) {}
