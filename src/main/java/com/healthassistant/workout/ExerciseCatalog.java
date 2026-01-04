package com.healthassistant.workout;

import com.healthassistant.workout.api.dto.ExerciseDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
class ExerciseCatalog {

    private static final List<String> MUSCLE_GROUPS = List.of(
            "CHEST",
            "UPPER_BACK",
            "LOWER_BACK",
            "TRAPS",
            "SHOULDERS_FRONT",
            "SHOULDERS_SIDE",
            "SHOULDERS_REAR",
            "BICEPS",
            "TRICEPS",
            "FOREARMS",
            "ABS",
            "OBLIQUES",
            "GLUTES",
            "QUADS",
            "HAMSTRINGS",
            "CALVES"
    );

    private final ExerciseDefinitionRepository repository;

    List<ExerciseDefinition> getAllExercises() {
        return repository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    List<ExerciseDefinition> getExercisesByMuscle(String muscle) {
        String normalizedMuscle = muscle.toUpperCase(Locale.ROOT);
        return repository.findByMuscle(normalizedMuscle).stream()
                .map(this::toDto)
                .toList();
    }

    List<String> getMuscleGroups() {
        return MUSCLE_GROUPS;
    }

    private ExerciseDefinition toDto(ExerciseDefinitionEntity entity) {
        return new ExerciseDefinition(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrimaryMuscle(),
                entity.getMuscles()
        );
    }
}
