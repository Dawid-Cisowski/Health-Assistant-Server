package com.healthassistant.workoutimport;

import com.healthassistant.guardrails.api.GuardrailFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutImageExtractor {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final ChatClient chatClient;
    private final ExerciseMatcher exerciseMatcher;
    @SuppressWarnings("unused") // Reserved for future image content moderation
    private final GuardrailFacade guardrailFacade;

    ExtractedWorkoutData extract(MultipartFile image) throws WorkoutExtractionException {
        try {
            byte[] imageBytes = image.getBytes();
            String mimeType = image.getContentType();

            log.info("Extracting workout data from image: {} bytes, type: {}", imageBytes.length, mimeType);

            AiWorkoutExtractionResponse response = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(userSpec -> userSpec
                    .text(buildUserPrompt())
                    .media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes))
                )
                .call()
                .entity(AiWorkoutExtractionResponse.class);

            if (response == null) {
                throw new WorkoutExtractionException("AI returned null response");
            }

            log.debug("AI response received, isWorkoutScreenshot: {}", response.isWorkoutScreenshot());

            return transformToExtractedWorkoutData(response);

        } catch (IOException e) {
            throw new WorkoutExtractionException("Failed to read image: " + e.getMessage(), e);
        } catch (WorkoutExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI extraction failed", e);
            throw new WorkoutExtractionException("AI extraction failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        String catalogSection = exerciseMatcher.buildCatalogPromptSection();

        return """
            You are an expert at analyzing workout app screenshots, particularly from GymRun.

            Your task is to extract workout data from the screenshot and return it as JSON.

            %s

            EXERCISE MATCHING RULES:
            1. For each exercise found in the screenshot, try to match it to an exercise in the catalog above
            2. Match by semantic similarity - exercise names may be abbreviated, in different languages, or use variations
            3. Common Polish-English matches: "Wyciskanie" = "Press", "Podciąganie" = "Pull ups", "Martwy ciąg" = "Deadlift"
            4. If you find a confident match (>0.7), set exerciseId to the catalog ID
            5. If uncertain (0.4-0.7), set exerciseId to your best guess and lower matchConfidence
            6. If NO match possible, set isNewExercise: true and provide suggested details

            IMPORTANT RULES:
            1. Return ONLY valid JSON without any additional text
            2. If the image is NOT a workout summary, return JSON with "isWorkoutScreenshot": false
            3. All numeric fields must be numbers (not strings)
            4. Keep exercise names in their original language (usually Polish)
            5. If weight is not specified, it's a bodyweight exercise (weightKg: 0)
            6. Date and time should be in ISO-8601 UTC format
            7. Report your confidence level (0.0 to 1.0)

            RESPONSE FORMAT (JSON Schema):
            {
              "isWorkoutScreenshot": boolean,
              "confidence": number (0.0-1.0),
              "performedAt": "ISO-8601 datetime string" or null,
              "note": "workout title/name" or null,
              "exercises": [
                {
                  "name": "original exercise name from screenshot",
                  "exerciseId": "catalog_id" or null (if isNewExercise),
                  "matchConfidence": number (0.0-1.0),
                  "isNewExercise": boolean,
                  "suggestedId": "muscle_N" (only if isNewExercise),
                  "suggestedPrimaryMuscle": "CHEST|UPPER_BACK|LOWER_BACK|QUADS|..." (only if isNewExercise),
                  "suggestedDescription": "exercise description" (only if isNewExercise),
                  "muscleGroup": "muscle group" or null,
                  "orderInWorkout": number (1-based),
                  "sets": [
                    {
                      "setNumber": number (1-based),
                      "weightKg": number (>= 0),
                      "reps": number (>= 1),
                      "isWarmup": boolean
                    }
                  ]
                }
              ],
              "validationError": "error description" or null
            }

            RECOGNITION TIPS:
            - GymRun shows workout with date at the top
            - Exercises are grouped with names and sets below
            - Sets contain: weight (kg) x reps
            - Warmup sets may be marked differently or have lower weight
            """.formatted(catalogSection);
    }

    private String buildUserPrompt() {
        return """
            Analyze this workout app screenshot.
            Extract all workout data and return as JSON.

            If this is not a workout summary screenshot, return:
            {"isWorkoutScreenshot": false, "confidence": 0.9, "validationError": "Reason for rejection"}
            """;
    }

    private ExtractedWorkoutData transformToExtractedWorkoutData(AiWorkoutExtractionResponse response) {
        double confidence = response.confidence();
        if (confidence < 0.0 || confidence > 1.0) {
            log.warn("AI returned invalid confidence: {}, clamping to [0.0, 1.0]", confidence);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }

        if (!response.isWorkoutScreenshot()) {
            String error = response.validationError() != null
                    ? response.validationError()
                    : "Not a workout screenshot";
            return ExtractedWorkoutData.invalid(error, confidence);
        }

        Instant performedAt = parsePerformedAt(response.performedAt());
        String note = response.note();

        List<ExtractedWorkoutData.Exercise> exercises = transformExercises(response.exercises());

        if (exercises.isEmpty()) {
            return ExtractedWorkoutData.invalid("No exercises found in image", confidence);
        }

        log.info("Successfully extracted workout: {} exercises, confidence: {}", exercises.size(), confidence);
        return ExtractedWorkoutData.valid(performedAt, note, exercises, confidence);
    }

    private List<ExtractedWorkoutData.Exercise> transformExercises(List<AiWorkoutExtractionResponse.AiExercise> aiExercises) {
        if (aiExercises == null || aiExercises.isEmpty()) {
            return List.of();
        }

        return aiExercises.stream()
                .map(this::transformExercise)
                .toList();
    }

    private ExtractedWorkoutData.Exercise transformExercise(AiWorkoutExtractionResponse.AiExercise aiExercise) {
        String name = aiExercise.name() != null ? aiExercise.name() : "Unknown Exercise";
        String exerciseId = aiExercise.exerciseId();
        double matchConfidence = aiExercise.matchConfidence() != null ? aiExercise.matchConfidence() : 0.0;
        boolean isNewExercise = aiExercise.isNewExercise() != null && aiExercise.isNewExercise();
        String suggestedId = aiExercise.suggestedId();
        String suggestedPrimaryMuscle = aiExercise.suggestedPrimaryMuscle();
        String suggestedDescription = aiExercise.suggestedDescription();
        String muscleGroup = aiExercise.muscleGroup();
        int order = aiExercise.orderInWorkout() != null ? aiExercise.orderInWorkout() : 1;

        List<ExtractedWorkoutData.ExerciseSet> sets = transformSets(aiExercise.sets());

        return new ExtractedWorkoutData.Exercise(
                name, exerciseId, matchConfidence, isNewExercise,
                suggestedId, suggestedPrimaryMuscle, suggestedDescription,
                muscleGroup, order, sets
        );
    }

    private List<ExtractedWorkoutData.ExerciseSet> transformSets(List<AiWorkoutExtractionResponse.AiExerciseSet> aiSets) {
        if (aiSets == null || aiSets.isEmpty()) {
            return List.of();
        }

        return aiSets.stream()
                .map(this::transformSet)
                .toList();
    }

    private ExtractedWorkoutData.ExerciseSet transformSet(AiWorkoutExtractionResponse.AiExerciseSet aiSet) {
        int setNumber = aiSet.setNumber() != null ? aiSet.setNumber() : 1;
        double weightKg = aiSet.weightKg() != null ? aiSet.weightKg() : 0.0;
        int reps = aiSet.reps() != null ? aiSet.reps() : 1;
        boolean isWarmup = aiSet.isWarmup() != null && aiSet.isWarmup();

        return new ExtractedWorkoutData.ExerciseSet(setNumber, weightKg, reps, isWarmup);
    }

    private Instant parsePerformedAt(String performedAtStr) {
        if (performedAtStr == null || performedAtStr.isBlank()) {
            return Instant.now();
        }

        try {
            Instant parsed = Instant.parse(performedAtStr);
            return validateAndClampDate(parsed);
        } catch (DateTimeParseException e) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(performedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                Instant parsed = localDateTime.atZone(POLAND_ZONE).toInstant();
                return validateAndClampDate(parsed);
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse performedAt: {}, using current time", performedAtStr);
                return Instant.now();
            }
        }
    }

    private Instant validateAndClampDate(Instant date) {
        Instant now = Instant.now();
        Instant tenYearsAgo = now.minus(3650, ChronoUnit.DAYS);

        if (date.isAfter(now)) {
            log.warn("AI returned future performedAt: {}, using current time", date);
            return now;
        }
        if (date.isBefore(tenYearsAgo)) {
            log.warn("AI returned performedAt more than 10 years ago: {}, using current time", date);
            return now;
        }
        return date;
    }
}
