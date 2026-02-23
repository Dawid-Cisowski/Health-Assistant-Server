package com.healthassistant.mealimport.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealDraftResponse(
    String draftId,
    String status,
    Instant suggestedOccurredAt,
    String description,
    MealData meal,
    Double confidence,
    List<ClarifyingQuestion> questions,
    Instant expiresAt,
    String errorMessage,
    List<DraftItem> items
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

    public record DraftItem(
            String title,
            String source,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            String healthRating
    ) {}

    public static MealDraftResponse success(
        String draftId,
        Instant suggestedOccurredAt,
        String description,
        MealData meal,
        double confidence,
        List<ClarifyingQuestion> questions,
        Instant expiresAt
    ) {
        return new MealDraftResponse(
            draftId, "draft", suggestedOccurredAt, description, meal,
            confidence, questions, expiresAt, null, null
        );
    }

    public static MealDraftResponse success(
        String draftId,
        Instant suggestedOccurredAt,
        String description,
        MealData meal,
        double confidence,
        List<ClarifyingQuestion> questions,
        Instant expiresAt,
        List<DraftItem> items
    ) {
        return new MealDraftResponse(
            draftId, "draft", suggestedOccurredAt, description, meal,
            confidence, questions, expiresAt, null, items
        );
    }

    public static MealDraftResponse failure(String errorMessage) {
        return new MealDraftResponse(
            null, "failed", null, null, null,
            null, null, null, errorMessage, null
        );
    }
}
