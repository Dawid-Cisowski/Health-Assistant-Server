package com.healthassistant.mealimport.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealImportResponse(
    String status,
    String mealId,
    String eventId,
    Instant occurredAt,
    String title,
    String mealType,
    Integer caloriesKcal,
    Integer proteinGrams,
    Integer fatGrams,
    Integer carbohydratesGrams,
    String healthRating,
    Double confidence,
    String errorMessage
) {
    public static MealImportResponse success(
        String mealId,
        String eventId,
        Instant occurredAt,
        String title,
        String mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating,
        double confidence
    ) {
        return new MealImportResponse(
            "success", mealId, eventId, occurredAt, title, mealType,
            caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
            healthRating, confidence, null
        );
    }

    public static MealImportResponse failure(String errorMessage) {
        return new MealImportResponse(
            "failed", null, null, null, null, null,
            null, null, null, null,
            null, null, errorMessage
        );
    }
}
