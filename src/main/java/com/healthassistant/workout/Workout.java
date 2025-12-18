package com.healthassistant.workout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

record Workout(
        String workoutId,
        Instant performedAt,
        LocalDate performedDate,
        String source,
        String note,
        String deviceId,
        String eventId,
        List<Exercise> exercises
) {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    Workout {
        Objects.requireNonNull(workoutId, "workoutId cannot be null");
        Objects.requireNonNull(performedAt, "performedAt cannot be null");
        Objects.requireNonNull(exercises, "exercises cannot be null");
        exercises = List.copyOf(exercises);
    }

    static Workout create(
            String workoutId,
            Instant performedAt,
            String source,
            String note,
            String deviceId,
            String eventId,
            List<Exercise> exercises
    ) {
        LocalDate performedDate = performedAt.atZone(POLAND_ZONE).toLocalDate();
        return new Workout(workoutId, performedAt, performedDate, source, note, deviceId, eventId, exercises);
    }

    int totalExercises() {
        return exercises.size();
    }

    int totalSets() {
        return exercises.stream()
                .mapToInt(Exercise::totalSets)
                .sum();
    }

    Volume totalVolume() {
        return exercises.stream()
                .map(Exercise::totalVolume)
                .reduce(Volume.ZERO, Volume::add);
    }

    Volume workingVolume() {
        return exercises.stream()
                .map(Exercise::workingVolume)
                .reduce(Volume.ZERO, Volume::add);
    }
}
