package com.healthassistant.workoutimport;

import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
class ExerciseMatcher {

    private final WorkoutFacade workoutFacade;

    String buildCatalogPromptSection() {
        List<ExerciseDefinition> exercises = workoutFacade.getAllExercises();

        Map<String, List<ExerciseDefinition>> byMuscle = exercises.stream()
                .collect(Collectors.groupingBy(ExerciseDefinition::primaryMuscle));

        StringBuilder sb = new StringBuilder();
        sb.append("EXERCISE CATALOG (match exercises to these IDs):\n");

        byMuscle.forEach((muscle, muscleExercises) -> {
            sb.append("\n").append(muscle).append(":\n");
            muscleExercises.forEach(ex ->
                    sb.append(String.format("  - id: \"%s\", name: \"%s\"\n", ex.id(), ex.name()))
            );
        });

        return sb.toString();
    }

    String resolveExerciseId(ExtractedWorkoutData.Exercise extracted) {
        if (extracted.exerciseId() != null && !extracted.isNewExercise()) {
            if (workoutFacade.exerciseExists(extracted.exerciseId())) {
                log.debug("Matched exercise '{}' to catalog ID: {}", extracted.name(), extracted.exerciseId());
                return extracted.exerciseId();
            }
            log.warn("AI returned non-existent exerciseId '{}' for exercise '{}'",
                    extracted.exerciseId(), extracted.name());
        }

        return createNewExercise(extracted);
    }

    private String createNewExercise(ExtractedWorkoutData.Exercise extracted) {
        String newId = generateUniqueId(extracted.suggestedId(), extracted.suggestedPrimaryMuscle());
        String primaryMuscle = extracted.suggestedPrimaryMuscle() != null
                ? extracted.suggestedPrimaryMuscle()
                : determineMuscleFromGroup(extracted.muscleGroup());
        String description = extracted.suggestedDescription() != null
                ? extracted.suggestedDescription()
                : "Auto-created exercise from workout import: " + extracted.name();

        workoutFacade.createAutoExercise(
                newId,
                extracted.name(),
                description,
                primaryMuscle,
                List.of(primaryMuscle)
        );

        log.info("Created new exercise from AI import: id={}, name={}, primaryMuscle={}, matchConfidence={}, suggestedId={}",
                newId, extracted.name(), primaryMuscle, extracted.matchConfidence(), extracted.suggestedId());
        return newId;
    }

    private String generateUniqueId(String suggestedId, String primaryMuscle) {
        if (suggestedId != null && !workoutFacade.exerciseExists(suggestedId)) {
            return suggestedId;
        }

        String prefix = primaryMuscle != null
                ? primaryMuscle.toLowerCase(Locale.ROOT)
                : "other";

        String uuidSuffix = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "_auto_" + uuidSuffix;
    }

    private String determineMuscleFromGroup(String muscleGroup) {
        if (muscleGroup == null || muscleGroup.isBlank()) {
            return "OTHER";
        }

        String normalized = muscleGroup.toLowerCase(Locale.ROOT);

        if (normalized.contains("klatka") || normalized.contains("chest") || normalized.contains("piersiow")) {
            return "CHEST";
        }
        if (normalized.contains("plecy") || normalized.contains("back")) {
            return "UPPER_BACK";
        }
        if (normalized.contains("nogi") || normalized.contains("legs") || normalized.contains("uda")) {
            return "QUADS";
        }
        if (normalized.contains("barki") || normalized.contains("shoulder")) {
            return "SHOULDERS_FRONT";
        }
        if (normalized.contains("biceps")) {
            return "BICEPS";
        }
        if (normalized.contains("triceps")) {
            return "TRICEPS";
        }
        if (normalized.contains("brzuch") || normalized.contains("abs")) {
            return "ABS";
        }

        return "OTHER";
    }
}
