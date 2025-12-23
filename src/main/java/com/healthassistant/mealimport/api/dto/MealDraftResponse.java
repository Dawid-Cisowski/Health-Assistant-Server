package com.healthassistant.mealimport.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealDraftResponse(
    String draftId,
    String status,
    Instant suggestedOccurredAt,
    MealData meal,
    Double confidence,
    List<ClarifyingQuestion> questions,
    Instant expiresAt,
    String errorMessage
) {
    public record MealData(
        String title,
        String mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating
    ) {
    }

    public static MealDraftResponse success(
        String draftId,
        Instant suggestedOccurredAt,
        MealData meal,
        double confidence,
        List<ClarifyingQuestion> questions,
        Instant expiresAt
    ) {
        return new MealDraftResponse(
            draftId, "draft", suggestedOccurredAt, meal,
            confidence, questions, expiresAt, null
        );
    }

    public static MealDraftResponse failure(String errorMessage) {
        return new MealDraftResponse(
            null, "failed", null, null,
            null, null, null, errorMessage
        );
    }
}
