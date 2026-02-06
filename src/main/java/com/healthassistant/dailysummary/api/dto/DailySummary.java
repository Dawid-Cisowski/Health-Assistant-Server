package com.healthassistant.dailysummary.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

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
        exercises = List.copyOf(exercises);
        workouts = List.copyOf(workouts);
        sleep = List.copyOf(sleep);
        meals = List.copyOf(meals);
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
        String note,
        java.time.Instant performedAt
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

    public String toAiDataContext(UnaryOperator<String> sanitizer) {
        StringBuilder sb = new StringBuilder();

        appendIfPositive(sb, "Steps", activity.steps());
        appendIfPositive(sb, "Active minutes", activity.activeMinutes());
        appendIfPositive(sb, "Active calories burned (kcal)", activity.activeCalories());
        if (activity.distanceMeters() != null && activity.distanceMeters() > 0) {
            sb.append("Distance: ").append(activity.distanceMeters()).append(" meters\n");
        }

        if (!sleep.isEmpty()) {
            int totalSleepMins = sleep.stream()
                    .mapToInt(s -> s.totalMinutes() != null ? s.totalMinutes() : 0)
                    .sum();
            sb.append("Sleep: ").append(totalSleepMins / 60).append("h ").append(totalSleepMins % 60).append("min\n");
        }

        if (!workouts.isEmpty()) {
            sb.append("Workouts: ").append(workouts.size()).append("\n");
            workouts.stream()
                    .filter(w -> w.note() != null && !w.note().isBlank())
                    .map(w -> "  - " + sanitizer.apply(w.note()))
                    .forEach(line -> sb.append(line).append("\n"));
        }

        if (nutrition.mealCount() != null && nutrition.mealCount() > 0) {
            sb.append("Meals: ").append(nutrition.mealCount()).append("\n");
            appendIfPositive(sb, "  Calories (kcal)", nutrition.totalCalories());
            appendIfPositive(sb, "  Protein (g)", nutrition.totalProtein());
            appendIfPositive(sb, "  Fat (g)", nutrition.totalFat());
            appendIfPositive(sb, "  Carbs (g)", nutrition.totalCarbs());
        }

        if (!meals.isEmpty()) {
            long healthyCount = meals.stream()
                    .filter(m -> "VERY_HEALTHY".equals(m.healthRating()) || "HEALTHY".equals(m.healthRating()))
                    .count();
            long unhealthyCount = meals.stream()
                    .filter(m -> "VERY_UNHEALTHY".equals(m.healthRating()) || "UNHEALTHY".equals(m.healthRating()))
                    .count();
            if (healthyCount > 0) sb.append("Healthy meals: ").append(healthyCount).append("\n");
            if (unhealthyCount > 0) sb.append("Unhealthy meals: ").append(unhealthyCount).append("\n");
        }

        appendIfPositive(sb, "Resting HR (bpm)", heart.restingBpm());
        appendIfPositive(sb, "Average HR (bpm)", heart.avgBpm());
        appendIfPositive(sb, "Max HR (bpm)", heart.maxBpm());

        return sb.toString();
    }

    private static void appendIfPositive(StringBuilder sb, String label, Integer value) {
        if (value != null && value > 0) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    // Helper methods for easier AI tool access
    public Integer getTotalSteps() {
        return activity != null ? activity.steps() : null;
    }

    public Integer getActiveCalories() {
        return activity != null ? activity.activeCalories() : null;
    }

    public Integer getTotalSleepMinutes() {
        return sleep.stream()
            .mapToInt(Sleep::totalMinutes)
            .sum();
    }

    public int getMealCount() {
        return meals.size();
    }

    public int getWorkoutCount() {
        return workouts.size();
    }
}
