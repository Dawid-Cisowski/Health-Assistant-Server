package com.healthassistant.mealimport;

class MealExtractionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    MealExtractionException(String message) {
        super(message);
    }

    MealExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
