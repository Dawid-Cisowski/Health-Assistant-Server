package com.healthassistant.dailysummary.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Daily health summary")
public class DailySummaryResponse {

    @JsonProperty("date")
    @Schema(description = "Date for which the summary is generated (YYYY-MM-DD)", example = "2025-11-12")
    private LocalDate date;

    @JsonProperty("activity")
    @Schema(description = "Daily activity metrics")
    private Activity activity;

    @JsonProperty("exercises")
    @Schema(description = "List of exercises performed during the day")
    private List<Exercise> exercises;

    @JsonProperty("workouts")
    @Schema(description = "List of gym workouts performed during the day")
    private List<Workout> workouts;

    @JsonProperty("sleep")
    @Schema(description = "Sleep sessions during the day (including naps)")
    private List<Sleep> sleep;

    @JsonProperty("heart")
    @Schema(description = "Heart rate metrics")
    private Heart heart;

    @JsonProperty("nutrition")
    @Schema(description = "Daily nutrition summary")
    private Nutrition nutrition;

    @JsonProperty("meals")
    @Schema(description = "List of meals consumed during the day")
    private List<Meal> meals;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Daily activity metrics")
    public static class Activity {
        @JsonProperty("steps")
        @Schema(description = "Total steps", example = "15631", nullable = true)
        private Integer steps;

        @JsonProperty("activeMinutes")
        @Schema(description = "Total active minutes", example = "87", nullable = true)
        private Integer activeMinutes;

        @JsonProperty("activeCalories")
        @Schema(description = "Total active calories burned", example = "560", nullable = true)
        private Integer activeCalories;

        @JsonProperty("distanceMeters")
        @Schema(description = "Total distance in meters", example = "7100", nullable = true)
        private Long distanceMeters;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Exercise session")
    public static class Exercise {
        @JsonProperty("type")
        @Schema(description = "Exercise type (e.g., WALK, running, cycling)", example = "WALK")
        private String type;

        @JsonProperty("start")
        @Schema(description = "Exercise start time (ISO-8601 UTC)", example = "2025-11-12T17:20:00Z")
        private Instant start;

        @JsonProperty("end")
        @Schema(description = "Exercise end time (ISO-8601 UTC)", example = "2025-11-12T17:55:00Z")
        private Instant end;

        @JsonProperty("durationMinutes")
        @Schema(description = "Exercise duration in minutes", example = "35", nullable = true)
        private Integer durationMinutes;

        @JsonProperty("distanceMeters")
        @Schema(description = "Distance covered in meters", example = "5200", nullable = true)
        private Long distanceMeters;

        @JsonProperty("steps")
        @Schema(description = "Total steps during exercise", example = "8000", nullable = true)
        private Integer steps;

        @JsonProperty("avgHr")
        @Schema(description = "Average heart rate in BPM", example = "141", nullable = true)
        private Integer avgHr;

        @JsonProperty("energyKcal")
        @Schema(description = "Calories burned during exercise", example = "320", nullable = true)
        private Integer energyKcal;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Gym workout session reference")
    public static class Workout {
        @JsonProperty("workoutId")
        @Schema(description = "Unique workout identifier", example = "gymrun-2025-11-19-1")
        private String workoutId;

        @JsonProperty("note")
        @Schema(description = "Workout note/description", example = "Plecy i biceps", nullable = true)
        private String note;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Sleep session information")
    public static class Sleep {
        @JsonProperty("start")
        @Schema(description = "Sleep start time (ISO-8601 UTC)", example = "2025-11-11T23:26:00Z", nullable = true)
        private Instant start;

        @JsonProperty("end")
        @Schema(description = "Sleep end time (ISO-8601 UTC)", example = "2025-11-12T06:00:00Z", nullable = true)
        private Instant end;

        @JsonProperty("totalMinutes")
        @Schema(description = "Total sleep duration in minutes", example = "394", nullable = true)
        private Integer totalMinutes;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Heart rate metrics")
    public static class Heart {
        @JsonProperty("restingBpm")
        @Schema(description = "Resting heart rate in BPM", example = "62", nullable = true)
        private Integer restingBpm;

        @JsonProperty("avgBpm")
        @Schema(description = "Average heart rate in BPM", example = "74", nullable = true)
        private Integer avgBpm;

        @JsonProperty("maxBpm")
        @Schema(description = "Maximum heart rate in BPM", example = "138", nullable = true)
        private Integer maxBpm;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Daily nutrition summary")
    public static class Nutrition {
        @JsonProperty("totalCalories")
        @Schema(description = "Total calories consumed in kcal", example = "2400", nullable = true)
        private Integer totalCalories;

        @JsonProperty("totalProtein")
        @Schema(description = "Total protein consumed in grams", example = "120", nullable = true)
        private Integer totalProtein;

        @JsonProperty("totalFat")
        @Schema(description = "Total fat consumed in grams", example = "80", nullable = true)
        private Integer totalFat;

        @JsonProperty("totalCarbs")
        @Schema(description = "Total carbohydrates consumed in grams", example = "250", nullable = true)
        private Integer totalCarbs;

        @JsonProperty("mealCount")
        @Schema(description = "Number of meals consumed", example = "5", nullable = true)
        private Integer mealCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Meal information")
    public static class Meal {
        @JsonProperty("title")
        @Schema(description = "Meal title/name", example = "Grilled chicken with rice", nullable = true)
        private String title;

        @JsonProperty("mealType")
        @Schema(description = "Type of meal", example = "LUNCH", nullable = true)
        private String mealType;

        @JsonProperty("caloriesKcal")
        @Schema(description = "Calories in kcal", example = "650", nullable = true)
        private Integer caloriesKcal;

        @JsonProperty("proteinGrams")
        @Schema(description = "Protein in grams", example = "45", nullable = true)
        private Integer proteinGrams;

        @JsonProperty("fatGrams")
        @Schema(description = "Fat in grams", example = "20", nullable = true)
        private Integer fatGrams;

        @JsonProperty("carbohydratesGrams")
        @Schema(description = "Carbohydrates in grams", example = "60", nullable = true)
        private Integer carbohydratesGrams;

        @JsonProperty("healthRating")
        @Schema(description = "Health rating of the meal", example = "HEALTHY", nullable = true)
        private String healthRating;

        @JsonProperty("occurredAt")
        @Schema(description = "Time when the meal was consumed (ISO-8601 UTC)", example = "2025-11-12T12:30:00Z", nullable = true)
        private Instant occurredAt;
    }
}
