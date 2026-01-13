package com.healthassistant.meals;

import java.io.Serial;

final class MealNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("PMD.UnusedFormalParameter")
    MealNotFoundException(String eventId) {
        // Don't include eventId in message to prevent information disclosure
        super("Meal not found");
    }
}
