package com.healthassistant.mealcatalog.api.dto;

import java.time.Instant;

public record CatalogProductResponse(
        String title,
        String mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating,
        Integer usageCount,
        Instant lastUsedAt
) {}
