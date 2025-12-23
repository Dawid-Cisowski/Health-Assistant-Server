package com.healthassistant.mealimport.api.dto;

import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;

import java.time.Instant;
import java.util.List;

public record MealDraftUpdateRequest(
    MealUpdateData meal,
    Instant occurredAt,
    List<QuestionAnswer> answers
) {
    public record MealUpdateData(
        String title,
        MealType mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        HealthRating healthRating
    ) {
    }
}
