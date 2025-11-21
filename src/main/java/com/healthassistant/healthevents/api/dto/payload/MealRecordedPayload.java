package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MealRecordedPayload(
        @JsonProperty("title")
        String title,

        @JsonProperty("mealType")
        MealType mealType,

        @JsonProperty("caloriesKcal")
        Integer caloriesKcal,

        @JsonProperty("proteinGrams")
        Integer proteinGrams,

        @JsonProperty("fatGrams")
        Integer fatGrams,

        @JsonProperty("carbohydratesGrams")
        Integer carbohydratesGrams,

        @JsonProperty("healthRating")
        HealthRating healthRating
) implements EventPayload {
}
