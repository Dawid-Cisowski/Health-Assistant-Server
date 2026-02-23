package com.healthassistant.mealcatalog.api.dto;

public record SaveProductRequest(
        String title,
        String mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating
) {
    private static final int MAX_TITLE_LENGTH = 500;

    public SaveProductRequest {
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters");
        }
        if (caloriesKcal != null && caloriesKcal < 0) {
            throw new IllegalArgumentException("caloriesKcal cannot be negative");
        }
        if (proteinGrams != null && proteinGrams < 0) {
            throw new IllegalArgumentException("proteinGrams cannot be negative");
        }
        if (fatGrams != null && fatGrams < 0) {
            throw new IllegalArgumentException("fatGrams cannot be negative");
        }
        if (carbohydratesGrams != null && carbohydratesGrams < 0) {
            throw new IllegalArgumentException("carbohydratesGrams cannot be negative");
        }
    }
}
