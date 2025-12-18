package com.healthassistant.calories;

record CaloriesAmount(double kcal) {

    static final CaloriesAmount ZERO = new CaloriesAmount(0.0);

    CaloriesAmount {
        if (kcal < 0) {
            throw new IllegalArgumentException("calories cannot be negative");
        }
    }

    static CaloriesAmount of(double kcal) {
        return new CaloriesAmount(kcal);
    }

    CaloriesAmount add(CaloriesAmount other) {
        return new CaloriesAmount(kcal + other.kcal);
    }

    boolean isPositive() {
        return kcal > 0;
    }

    boolean isGreaterThan(CaloriesAmount other) {
        return kcal > other.kcal;
    }
}
