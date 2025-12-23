package com.healthassistant.mealimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.mealimport.api.dto.ClarifyingQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class MealContentExtractor {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    ExtractedMealData extract(String description, List<MultipartFile> images) {
        log.info("Extracting meal data from description: {}, images: {}",
            description != null ? description.length() + " chars" : "none",
            images != null ? images.size() : 0);

        try {
            String response = callAI(description, images);
            return parseExtractionResponse(response);
        } catch (IOException e) {
            throw new MealExtractionException("Failed to read image data: " + e.getMessage(), e);
        }
    }

    private String callAI(String description, List<MultipartFile> images) throws IOException {
        var promptBuilder = chatClient.prompt()
            .system(buildSystemPrompt());

        return promptBuilder.user(userSpec -> {
            userSpec.text(buildUserPrompt(description, images));

            if (images != null && !images.isEmpty()) {
                for (MultipartFile image : images) {
                    try {
                        String mimeType = getMimeType(image);
                        byte[] imageBytes = image.getBytes();
                        userSpec.media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes));
                    } catch (IOException e) {
                        log.warn("Failed to read image: {}", image.getOriginalFilename(), e);
                    }
                }
            }
        }).call().content();
    }

    private String getMimeType(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || contentType.equals("image/*") || contentType.equals("application/octet-stream")) {
            return "image/jpeg";
        }
        if (contentType.equals("image/jpg")) {
            return "image/jpeg";
        }
        return contentType;
    }

    private String buildSystemPrompt() {
        return """
            You are a nutrition expert analyzing meals from photos and/or descriptions.

            Your task is to estimate nutritional values and return them as JSON.

            IMPORTANT RULES:
            1. Return ONLY valid JSON without any additional text
            2. If input is NOT food-related, return JSON with "isMeal": false
            3. All numeric fields must be numbers (not strings)
            4. Estimate nutritional values based on typical portion sizes
            5. Be conservative with estimates - better to underestimate calories
            6. If time is mentioned in description, extract it; otherwise use null
            7. Report your confidence level (0.0 to 1.0)
            8. If confidence < 0.8 OR information is ambiguous, generate clarifying questions

            CLARIFYING QUESTIONS RULES:
            - Generate 0-3 questions maximum
            - Questions should help improve accuracy of nutritional estimates
            - Use Polish language for questions
            - Include which fields the answer would affect
            - Common scenarios requiring questions:
              * Unclear portion size (mala/srednia/duza porcja)
              * Multiple preparation methods possible (smazony/pieczony/gotowany)
              * Missing key ingredients (z sosem/bez sosu)
              * Ambiguous meal type

            QUESTION TYPES:
            - SINGLE_CHOICE: Provide 2-4 options
            - YES_NO: Boolean question
            - FREE_TEXT: Open answer (use sparingly)

            MEAL TYPES (choose one):
            - BREAKFAST: Morning meal (7-10am typical)
            - BRUNCH: Late morning (10am-12pm)
            - LUNCH: Midday meal (12-3pm)
            - DINNER: Evening meal (5-9pm)
            - SNACK: Small meal between main meals
            - DESSERT: Sweet course
            - DRINK: Beverages (coffee, smoothie, etc.)

            HEALTH RATINGS:
            - VERY_HEALTHY: Salads, lean proteins, vegetables
            - HEALTHY: Balanced meals, whole grains
            - NEUTRAL: Average processed foods
            - UNHEALTHY: Fast food, fried foods
            - VERY_UNHEALTHY: High sugar, high fat junk food

            RESPONSE FORMAT:
            {
              "isMeal": boolean,
              "confidence": number (0.0-1.0),
              "title": "dish name in Polish" or null,
              "mealType": "LUNCH" | "DINNER" | etc.,
              "occurredAt": "ISO-8601 datetime" or null,
              "caloriesKcal": number,
              "proteinGrams": number,
              "fatGrams": number,
              "carbohydratesGrams": number,
              "healthRating": "HEALTHY" | "NEUTRAL" | etc.,
              "validationError": "error description" or null,
              "questions": [
                {
                  "questionId": "q1",
                  "questionText": "Czy porcja byla mala, srednia czy duza?",
                  "questionType": "SINGLE_CHOICE",
                  "options": ["SMALL", "MEDIUM", "LARGE"],
                  "affectedFields": ["caloriesKcal", "proteinGrams", "fatGrams", "carbohydratesGrams"]
                }
              ]
            }

            ESTIMATION TIPS:
            - Typical lunch: 500-800 kcal
            - Typical dinner: 600-900 kcal
            - Protein per 100g meat: ~25g
            - Use visual portion estimation from photos
            - If only description, estimate based on typical recipes
            """;
    }

    private String buildUserPrompt(String description, List<MultipartFile> images) {
        StringBuilder prompt = new StringBuilder("Analyze this meal.\n");

        if (description != null && !description.isBlank()) {
            prompt.append("Description: ").append(description).append("\n");
        }

        if (images != null && !images.isEmpty()) {
            prompt.append("Photos attached showing the meal (").append(images.size()).append(" image(s)).\n");
        }

        prompt.append("\nExtract nutritional information and return as JSON.\n");
        prompt.append("If this is not food-related, return:\n");
        prompt.append("{\"isMeal\": false, \"confidence\": X, \"validationError\": \"Reason\"}");

        return prompt.toString();
    }

    private ExtractedMealData parseExtractionResponse(String response) {
        try {
            String cleanedResponse = cleanJsonResponse(response);
            JsonNode root = objectMapper.readTree(cleanedResponse);

            boolean isMeal = root.path("isMeal").asBoolean(false);
            double confidence = root.path("confidence").asDouble(0.0);

            if (!isMeal) {
                String error = root.path("validationError").asText("Not a meal image/description");
                return ExtractedMealData.invalid(error, confidence);
            }

            // Extract timestamp if present
            Instant occurredAt = parseOccurredAt(root);

            String title = getTextOrNull(root, "title");
            String mealType = getTextOrNull(root, "mealType");
            Integer caloriesKcal = getIntOrNull(root, "caloriesKcal");
            Integer proteinGrams = getIntOrNull(root, "proteinGrams");
            Integer fatGrams = getIntOrNull(root, "fatGrams");
            Integer carbohydratesGrams = getIntOrNull(root, "carbohydratesGrams");
            String healthRating = getTextOrNull(root, "healthRating");

            if (title == null || mealType == null) {
                return ExtractedMealData.invalid("Missing required fields (title or mealType)", confidence);
            }

            if (caloriesKcal == null || proteinGrams == null || fatGrams == null || carbohydratesGrams == null) {
                return ExtractedMealData.invalid("Missing nutritional values", confidence);
            }

            if (healthRating == null) {
                healthRating = "NEUTRAL";
            }

            List<ClarifyingQuestion> questions = parseQuestions(root);

            return ExtractedMealData.validWithQuestions(
                occurredAt, title, mealType,
                caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
                healthRating, confidence, questions
            );

        } catch (JsonProcessingException e) {
            throw new MealExtractionException(
                "Failed to parse AI response as JSON: " + e.getMessage(), e
            );
        }
    }

    private List<ClarifyingQuestion> parseQuestions(JsonNode root) {
        JsonNode questionsNode = root.path("questions");
        if (questionsNode.isMissingNode() || !questionsNode.isArray()) {
            return List.of();
        }

        List<ClarifyingQuestion> questions = new ArrayList<>();
        for (JsonNode questionNode : questionsNode) {
            String questionId = getTextOrNull(questionNode, "questionId");
            String questionText = getTextOrNull(questionNode, "questionText");
            String questionTypeStr = getTextOrNull(questionNode, "questionType");

            if (questionId == null || questionText == null || questionTypeStr == null) {
                continue;
            }

            ClarifyingQuestion.QuestionType questionType;
            try {
                questionType = ClarifyingQuestion.QuestionType.valueOf(questionTypeStr);
            } catch (IllegalArgumentException e) {
                log.debug("Unknown question type: {}", questionTypeStr);
                questionType = ClarifyingQuestion.QuestionType.FREE_TEXT;
            }

            List<String> options = parseStringArray(questionNode.path("options"));
            List<String> affectedFields = parseStringArray(questionNode.path("affectedFields"));

            questions.add(new ClarifyingQuestion(
                questionId, questionText, questionType, options, affectedFields
            ));
        }

        return questions;
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        if (arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String cleanJsonResponse(String response) {
        if (response == null) {
            return "{}";
        }
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private Instant parseOccurredAt(JsonNode root) {
        JsonNode occurredAtNode = root.path("occurredAt");
        if (occurredAtNode.isMissingNode() || occurredAtNode.isNull()) {
            return null;
        }
        try {
            return Instant.parse(occurredAtNode.asText());
        } catch (Exception e) {
            log.debug("Could not parse occurredAt: {}", occurredAtNode.asText());
            return null;
        }
    }

    private String getTextOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Integer getIntOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asInt();
    }
}
