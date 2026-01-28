package com.healthassistant.mealimport;

import com.healthassistant.mealimport.api.dto.ClarifyingQuestion;

import java.time.Instant;
import java.util.List;

record ExtractedMealData(
        boolean isValid,
        String validationError,
        Instant occurredAt,
        String title,
        String description,
        String mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating,
        double confidence,
        List<ClarifyingQuestion> questions,
        Long promptTokens,
        Long completionTokens
) {
    static ExtractedMealData valid(
            Instant occurredAt,
            String title,
            String description,
            String mealType,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            String healthRating,
            double confidence
    ) {
        return new ExtractedMealData(
                true, null, occurredAt, title, description, mealType,
                caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
                healthRating, confidence, List.of(), null, null
        );
    }

    static ExtractedMealData validWithQuestions(
            Instant occurredAt,
            String title,
            String description,
            String mealType,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            String healthRating,
            double confidence,
            List<ClarifyingQuestion> questions
    ) {
        return new ExtractedMealData(
                true, null, occurredAt, title, description, mealType,
                caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
                healthRating, confidence, questions != null ? questions : List.of(), null, null
        );
    }

    static ExtractedMealData validWithTokens(
            Instant occurredAt,
            String title,
            String description,
            String mealType,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            String healthRating,
            double confidence,
            List<ClarifyingQuestion> questions,
            Long promptTokens,
            Long completionTokens
    ) {
        return new ExtractedMealData(
                true, null, occurredAt, title, description, mealType,
                caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
                healthRating, confidence, questions != null ? questions : List.of(),
                promptTokens, completionTokens
        );
    }

    static ExtractedMealData invalid(String error, double confidence) {
        return new ExtractedMealData(
                false, error, null, null, null, null,
                null, null, null, null,
                null, confidence, List.of(), null, null
        );
    }
}
