package com.healthassistant.meals;

record Macros(
        int caloriesKcal,
        int proteinGrams,
        int fatGrams,
        int carbohydratesGrams
) {

    static final Macros ZERO = new Macros(0, 0, 0, 0);

    Macros {
        if (caloriesKcal < 0) {
            throw new IllegalArgumentException("caloriesKcal cannot be negative");
        }
        if (proteinGrams < 0) {
            throw new IllegalArgumentException("proteinGrams cannot be negative");
        }
        if (fatGrams < 0) {
            throw new IllegalArgumentException("fatGrams cannot be negative");
        }
        if (carbohydratesGrams < 0) {
            throw new IllegalArgumentException("carbohydratesGrams cannot be negative");
        }
    }

    static Macros of(Integer calories, Integer protein, Integer fat, Integer carbs) {
        return new Macros(
                calories != null ? calories : 0,
                protein != null ? protein : 0,
                fat != null ? fat : 0,
                carbs != null ? carbs : 0
        );
    }

    Macros add(Macros other) {
        return new Macros(
                caloriesKcal + other.caloriesKcal,
                proteinGrams + other.proteinGrams,
                fatGrams + other.fatGrams,
                carbohydratesGrams + other.carbohydratesGrams
        );
    }
}
