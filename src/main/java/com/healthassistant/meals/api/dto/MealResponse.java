package com.healthassistant.meals.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "Response after creating or updating a meal")
public record MealResponse(
    @JsonProperty("eventId")
    @Schema(description = "Unique identifier of the meal event", example = "evt_abc123")
    String eventId,

    @JsonProperty("date")
    @Schema(description = "Date of the meal (in Poland timezone)", example = "2025-11-19")
    LocalDate date,

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
