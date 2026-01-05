package com.healthassistant.workout;

import java.util.List;
import java.util.Objects;

record Exercise(
        String name,
        String exerciseId,
        String muscleGroup,
        int orderInWorkout,
        List<ExerciseSet> sets
) {

    private static final int MAX_EXERCISE_ID_LENGTH = 50;

    Exercise {
        Objects.requireNonNull(name, "exercise name cannot be null");
        Objects.requireNonNull(sets, "sets cannot be null");
        if (exerciseId != null && exerciseId.length() > MAX_EXERCISE_ID_LENGTH) {
            throw new IllegalArgumentException("exerciseId must not exceed " + MAX_EXERCISE_ID_LENGTH + " characters");
        }
        if (orderInWorkout < 1) {
            throw new IllegalArgumentException("orderInWorkout must be at least 1");
        }
        sets = List.copyOf(sets);
    }

    int totalSets() {
        return sets.size();
    }

    Volume totalVolume() {
        return sets.stream()
                .map(ExerciseSet::volume)
                .reduce(Volume.ZERO, Volume::add);
    }

    Volume workingVolume() {
        return sets.stream()
                .filter(ExerciseSet::isWorkingSet)
                .map(ExerciseSet::volume)
                .reduce(Volume.ZERO, Volume::add);
    }

    Weight maxWeight() {
        return sets.stream()
                .map(ExerciseSet::weight)
                .reduce(Weight.ZERO, Weight::max);
    }
}
