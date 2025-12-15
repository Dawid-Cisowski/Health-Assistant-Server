package com.healthassistant.mealimport.dto;

import java.time.Instant;

public record ExtractedMealData(
    boolean isValid,
    String validationError,
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
    public static ExtractedMealData valid(
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
        return new ExtractedMealData(
            true, null, occurredAt, title, mealType,
            caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
            healthRating, confidence
        );
    }

    public static ExtractedMealData invalid(String error, double confidence) {
        return new ExtractedMealData(
            false, error, null, null, null,
            null, null, null, null,
            null, confidence
        );
    }
}
