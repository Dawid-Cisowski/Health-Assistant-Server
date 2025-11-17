package com.healthassistant.domain.summary;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailySummary(
    LocalDate date,
    Activity activity,
    List<Exercise> exercises,
    Sleep sleep,
    Heart heart
) {
    public DailySummary {
        Objects.requireNonNull(date, "Date cannot be null");
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(exercises, "Exercises cannot be null");
        Objects.requireNonNull(sleep, "Sleep cannot be null");
        Objects.requireNonNull(heart, "Heart cannot be null");
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
}

