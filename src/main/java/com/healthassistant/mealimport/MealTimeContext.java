package com.healthassistant.mealimport;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

record MealTimeContext(
    LocalTime currentLocalTime,
    LocalDate currentDate,
    List<TodaysMeal> todaysMeals
) {
    static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    record TodaysMeal(String mealType, Instant occurredAt, String title) {
        String formattedTime() {
            return occurredAt.atZone(POLAND_ZONE).toLocalTime().format(TIME_FORMATTER);
        }
    }

    boolean hasBreakfast() {
        return todaysMeals.stream().anyMatch(m -> "BREAKFAST".equals(m.mealType()));
    }

    boolean hasBrunch() {
        return todaysMeals.stream().anyMatch(m -> "BRUNCH".equals(m.mealType()));
    }

    boolean hasLunch() {
        return todaysMeals.stream().anyMatch(m -> "LUNCH".equals(m.mealType()));
    }

    boolean hasDinner() {
        return todaysMeals.stream().anyMatch(m -> "DINNER".equals(m.mealType()));
    }

    String formatCurrentTime() {
        return currentLocalTime.format(TIME_FORMATTER);
    }

    String formatMealsForPrompt() {
        if (todaysMeals.isEmpty()) {
            return "No meals today";
        }
        return todaysMeals.stream()
            .map(meal -> "- " + meal.mealType() + " at " + meal.formattedTime() + ": " + meal.title())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    }
}
