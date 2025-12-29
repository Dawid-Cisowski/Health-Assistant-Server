package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

record Meal(
        String deviceId,
        String eventId,
        LocalDate date,
        Instant occurredAt,
        String title,
        MealType mealType,
        Macros macros,
        HealthRating healthRating
) {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    Meal {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(mealType, "mealType cannot be null");
        Objects.requireNonNull(macros, "macros cannot be null");
        Objects.requireNonNull(healthRating, "healthRating cannot be null");
    }

    static Meal create(
            String deviceId,
            String eventId,
            Instant occurredAt,
            String title,
            MealType mealType,
            Integer caloriesKcal,
            Integer proteinGrams,
            Integer fatGrams,
            Integer carbohydratesGrams,
            HealthRating healthRating
    ) {
        LocalDate date = occurredAt.atZone(POLAND_ZONE).toLocalDate();
        Macros macros = Macros.of(caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams);
        return new Meal(deviceId, eventId, date, occurredAt, title, mealType, macros, healthRating);
    }

    String mealTypeName() {
        return mealType.name();
    }

    String healthRatingName() {
        return healthRating.name();
    }

    int caloriesKcal() {
        return macros.caloriesKcal();
    }

    int proteinGrams() {
        return macros.proteinGrams();
    }

    int fatGrams() {
        return macros.fatGrams();
    }

    int carbohydratesGrams() {
        return macros.carbohydratesGrams();
    }
}
