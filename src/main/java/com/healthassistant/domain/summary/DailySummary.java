package com.healthassistant.domain.summary;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailySummary(
    LocalDate date,
    Activity activity,
    List<Workout> workouts,
    Sleep sleep,
    Heart heart,
    Score score
) {
    public DailySummary {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(workouts, "Workouts cannot be null");
        Objects.requireNonNull(sleep, "Sleep cannot be null");
        Objects.requireNonNull(heart, "Heart cannot be null");
        Objects.requireNonNull(score, "Score cannot be null");
    }

    public record Activity(
        Integer steps,
        Integer activeMinutes,
        Integer activeCalories,
        Double distanceMeters
    ) {
    }

    public record Workout(
        String type,
        java.time.Instant start,
        java.time.Instant end,
        Integer durationMinutes,
        Double distanceMeters,
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

    public record Score(
        Integer activityScore,
        Integer sleepScore,
        Integer readinessScore,
        Integer overallScore
    ) {
    }
}

