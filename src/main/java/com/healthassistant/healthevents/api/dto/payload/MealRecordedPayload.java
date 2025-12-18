package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MealRecordedPayload(
        @NotBlank(message = "title is required")
        @JsonProperty("title")
        String title,

        @NotNull(message = "mealType is required")
        @JsonProperty("mealType")
        MealType mealType,

        @Min(value = 0, message = "caloriesKcal must be non-negative")
        @JsonProperty("caloriesKcal")
        Integer caloriesKcal,

        @Min(value = 0, message = "proteinGrams must be non-negative")
        @JsonProperty("proteinGrams")
        Integer proteinGrams,

        @Min(value = 0, message = "fatGrams must be non-negative")
        @JsonProperty("fatGrams")
        Integer fatGrams,

        @Min(value = 0, message = "carbohydratesGrams must be non-negative")
        @JsonProperty("carbohydratesGrams")
        Integer carbohydratesGrams,

        @NotNull(message = "healthRating is required")
        @JsonProperty("healthRating")
        HealthRating healthRating
) implements EventPayload {
}
