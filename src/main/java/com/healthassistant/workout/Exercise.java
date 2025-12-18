package com.healthassistant.workout;

import java.util.List;
import java.util.Objects;

record Exercise(
        String name,
        String muscleGroup,
        int orderInWorkout,
        List<ExerciseSet> sets
) {

    Exercise {
        Objects.requireNonNull(name, "exercise name cannot be null");
        Objects.requireNonNull(sets, "sets cannot be null");
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
