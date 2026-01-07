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

    private static final int MAX_EXERCISE_ID_LENGTH = 100;
    private static final String VALID_ID_PATTERN = "^[a-zA-Z0-9_-]+$";

    private final WorkoutFacade workoutFacade;

    String buildCatalogPromptSection() {
        List<ExerciseDefinition> exercises = workoutFacade.getAllExercises();

        Map<String, List<ExerciseDefinition>> byMuscle = exercises.stream()
                .collect(Collectors.groupingBy(ExerciseDefinition::primaryMuscle));

        return byMuscle.entrySet().stream()
                .map(entry -> formatMuscleGroupSection(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("", "EXERCISE CATALOG (match exercises to these IDs):\n", ""));
    }

    private String formatMuscleGroupSection(String muscle, List<ExerciseDefinition> exercises) {
        String exerciseLines = exercises.stream()
                .map(ex -> String.format("  - id: \"%s\", name: \"%s\"",
                        sanitizeForPrompt(ex.id()), sanitizeForPrompt(ex.name())))
                .collect(Collectors.joining("\n"));
        return "\n" + sanitizeForPrompt(muscle) + ":\n" + exerciseLines + "\n";
    }

    private String sanitizeForPrompt(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[\"'\\n\\r\\t]", " ").replaceAll("\\s+", " ").trim();
    }

    String resolveExerciseId(ExtractedWorkoutData.Exercise extracted) {
        String sanitizedId = sanitizeExerciseId(extracted.exerciseId());
        if (sanitizedId != null && !extracted.isNewExercise()) {
            if (workoutFacade.exerciseExists(sanitizedId)) {
                log.debug("Matched exercise '{}' to catalog ID: {}", extracted.name(), sanitizedId);
                return sanitizedId;
            }
            log.warn("AI returned non-existent exerciseId '{}' for exercise '{}'",
                    sanitizedId, extracted.name());
        }

        return createNewExercise(extracted);
    }

    private String sanitizeExerciseId(String exerciseId) {
        if (exerciseId == null) {
            return null;
        }
        if (exerciseId.length() > MAX_EXERCISE_ID_LENGTH) {
            log.warn("AI returned exerciseId exceeding max length: {}", exerciseId.length());
            return null;
        }
        if (!exerciseId.matches(VALID_ID_PATTERN)) {
            log.warn("AI returned exerciseId with invalid characters: {}", exerciseId);
            return null;
        }
        return exerciseId;
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
