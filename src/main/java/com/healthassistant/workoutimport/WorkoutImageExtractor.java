package com.healthassistant.workoutimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutImageExtractor {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    ExtractedWorkoutData extract(MultipartFile image) throws WorkoutExtractionException {
        try {
            byte[] imageBytes = image.getBytes();
            String mimeType = image.getContentType();

            log.info("Extracting workout data from image: {} bytes, type: {}", imageBytes.length, mimeType);

            String response = chatClient.prompt()
                .system(buildSystemPrompt())
                .user(userSpec -> userSpec
                    .text(buildUserPrompt())
                    .media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes))
                )
                .call()
                .content();

            if (response == null || response.isBlank()) {
                throw new WorkoutExtractionException("AI returned empty response");
            }

            log.debug("AI response: {}", response);

            return parseExtractionResponse(response);

        } catch (IOException e) {
            throw new WorkoutExtractionException("Failed to read image: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("AI extraction failed", e);
            throw new WorkoutExtractionException("AI extraction failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt() {
        return """
            You are an expert at analyzing workout app screenshots, particularly from GymRun.

            Your task is to extract workout data from the screenshot and return it as JSON.

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
                  "name": "exercise name",
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
            """;
    }

    private String buildUserPrompt() {
        return """
            Analyze this workout app screenshot.
            Extract all workout data and return as JSON.

            If this is not a workout summary screenshot, return:
            {"isWorkoutScreenshot": false, "confidence": 0.9, "validationError": "Reason for rejection"}
            """;
    }

    private ExtractedWorkoutData parseExtractionResponse(String response) throws WorkoutExtractionException {
        try {
            String cleanedResponse = cleanJsonResponse(response);
            JsonNode root = objectMapper.readTree(cleanedResponse);

            boolean isWorkoutScreenshot = root.path("isWorkoutScreenshot").asBoolean(false);
            double confidence = root.path("confidence").asDouble(0.0);

            if (!isWorkoutScreenshot) {
                String error = root.path("validationError").asText("Not a workout screenshot");
                return ExtractedWorkoutData.invalid(error, confidence);
            }

            Instant performedAt = parsePerformedAt(root.path("performedAt").asText(null));
            String note = root.path("note").asText(null);

            List<ExtractedWorkoutData.Exercise> exercises = new ArrayList<>();
            JsonNode exercisesNode = root.path("exercises");

            if (exercisesNode.isArray()) {
                for (JsonNode exerciseNode : exercisesNode) {
                    exercises.add(parseExercise(exerciseNode));
                }
            }

            if (exercises.isEmpty()) {
                return ExtractedWorkoutData.invalid("No exercises found in image", confidence);
            }

            log.info("Successfully extracted workout: {} exercises, confidence: {}", exercises.size(), confidence);
            return ExtractedWorkoutData.valid(performedAt, note, exercises, confidence);

        } catch (JsonProcessingException e) {
            throw new WorkoutExtractionException("Failed to parse AI response as JSON: " + e.getMessage(), e);
        }
    }

    private String cleanJsonResponse(String response) {
        return response
            .replaceAll("```json\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();
    }

    private Instant parsePerformedAt(String performedAtStr) {
        if (performedAtStr == null || performedAtStr.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(performedAtStr);
        } catch (DateTimeParseException e) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(performedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return localDateTime.atZone(POLAND_ZONE).toInstant();
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse performedAt: {}, using current time", performedAtStr);
                return Instant.now();
            }
        }
    }

    private ExtractedWorkoutData.Exercise parseExercise(JsonNode node) {
        String name = node.path("name").asText("Unknown Exercise");
        String muscleGroup = parseMuscleGroup(node.path("muscleGroup"));
        int order = node.path("orderInWorkout").asInt(1);

        List<ExtractedWorkoutData.ExerciseSet> sets = new ArrayList<>();
        JsonNode setsNode = node.path("sets");

        if (setsNode.isArray()) {
            for (JsonNode setNode : setsNode) {
                sets.add(new ExtractedWorkoutData.ExerciseSet(
                    setNode.path("setNumber").asInt(1),
                    setNode.path("weightKg").asDouble(0.0),
                    setNode.path("reps").asInt(1),
                    setNode.path("isWarmup").asBoolean(false)
                ));
            }
        }

        return new ExtractedWorkoutData.Exercise(name, muscleGroup, order, sets);
    }

    private String parseMuscleGroup(JsonNode muscleGroupNode) {
        if (muscleGroupNode.isMissingNode() || muscleGroupNode.isNull()) {
            return null;
        }
        String value = muscleGroupNode.asText();
        if (value.isEmpty() || "null".equals(value)) {
            return null;
        }
        return value;
    }
}
