package com.healthassistant.mealimport.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

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
    String errorMessage,
    Long promptTokens,
    Long completionTokens,
    List<ImportedItem> items
) {
    public record ImportedItem(
            String title,
            String source,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            String healthRating
    ) {}

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
            healthRating, confidence, null, null, null, null
        );
    }

    public static MealImportResponse successWithTokens(
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
        double confidence,
        Long promptTokens,
        Long completionTokens
    ) {
        return new MealImportResponse(
            "success", mealId, eventId, occurredAt, title, mealType,
            caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
            healthRating, confidence, null, promptTokens, completionTokens, null
        );
    }

    public static MealImportResponse successWithTokens(
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
        double confidence,
        Long promptTokens,
        Long completionTokens,
        List<ImportedItem> items
    ) {
        return new MealImportResponse(
            "success", mealId, eventId, occurredAt, title, mealType,
            caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
            healthRating, confidence, null, promptTokens, completionTokens, items
        );
    }

    public static MealImportResponse failure(String errorMessage) {
        return new MealImportResponse(
            "failed", null, null, null, null, null,
            null, null, null, null,
            null, null, errorMessage, null, null, null
        );
    }
}
