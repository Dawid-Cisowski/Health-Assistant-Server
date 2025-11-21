package com.healthassistant.dailysummary.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailySummary(
    LocalDate date,
    Activity activity,
    List<Exercise> exercises,
    List<Workout> workouts,
    List<Sleep> sleep,
    Heart heart,
    Nutrition nutrition,
    List<Meal> meals
) {
    public DailySummary {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(exercises, "Exercises cannot be null");
        Objects.requireNonNull(workouts, "Workouts cannot be null");
        Objects.requireNonNull(sleep, "Sleep cannot be null");
        Objects.requireNonNull(heart, "Heart cannot be null");
        Objects.requireNonNull(nutrition, "Nutrition cannot be null");
        Objects.requireNonNull(meals, "Meals cannot be null");
    }

    public record Activity(
        Integer steps,
        Integer activeMinutes,
        Integer activeCalories,
        Long distanceMeters
    ) {
    }

    public record Exercise(
        String type,
        java.time.Instant start,
        java.time.Instant end,
        Integer durationMinutes,
        Long distanceMeters,
        Integer steps,
        Integer avgHr,
        Integer energyKcal
    ) {
    }

    public record Sleep(
        java.time.Instant start,
        java.time.Instant end,
        Integer totalMinutes
    ) {
    }

    public record Heart(
        Integer restingBpm,
        Integer avgBpm,
        Integer maxBpm
    ) {
    }

    public record Workout(
        String workoutId,
        String note
    ) {
    }

    public record Nutrition(
        Integer totalCalories,
        Integer totalProtein,
        Integer totalFat,
        Integer totalCarbs,
        Integer mealCount
    ) {
    }

    public record Meal(
        String title,
        String mealType,
        Integer caloriesKcal,
        Integer proteinGrams,
        Integer fatGrams,
        Integer carbohydratesGrams,
        String healthRating,
        java.time.Instant occurredAt
    ) {
    }
}
