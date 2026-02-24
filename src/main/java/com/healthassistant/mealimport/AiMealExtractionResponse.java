package com.healthassistant.mealimport;

import java.util.List;

record AiMealExtractionResponse(
        boolean isMeal,
        double confidence,
        Double mealTypeConfidence,
        String title,
        String description,
        String mealType,
        String occurredAt,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating,
        String validationError,
        List<AiQuestion> questions,
        List<AiItem> items
) {
    record AiQuestion(
            String questionId,
            String questionText,
            String questionType,
            List<String> options,
            List<String> affectedFields
    ) {}

    record AiItem(
            String title,
            String source,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            String healthRating
    ) {}
}
