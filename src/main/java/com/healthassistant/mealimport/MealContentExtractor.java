package com.healthassistant.mealimport;

import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.mealimport.api.dto.ClarifyingQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import com.healthassistant.mealimport.api.dto.QuestionAnswer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class MealContentExtractor {

    private static final double MEAL_TYPE_CONFIDENCE_THRESHOLD = 0.5;
    private static final int MAX_CALORIES = 10000;
    private static final int MAX_PROTEIN_GRAMS = 500;
    private static final int MAX_FAT_GRAMS = 500;
    private static final int MAX_CARBS_GRAMS = 1000;

    private final ChatClient chatClient;
    private final GuardrailFacade guardrailFacade;

    ExtractedMealData extract(String description, List<MultipartFile> images, MealTimeContext timeContext) {
        log.info("Extracting meal data from description: {}, images: {}, time: {}",
            description != null ? description.length() + " chars" : "none",
            images != null ? images.size() : 0,
            timeContext != null ? timeContext.formatCurrentTime() : "none");

        try {
            AiMealExtractionResponse response = callAI(description, images, timeContext);
            return transformToExtractedMealData(response);
        } catch (IOException e) {
            throw new MealExtractionException("Failed to read image data: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("AI extraction failed: {}", e.getMessage(), e);
            throw new MealExtractionException("Failed to extract meal data: " + e.getMessage(), e);
        }
    }

    private AiMealExtractionResponse callAI(String description, List<MultipartFile> images, MealTimeContext timeContext) throws IOException {
        var promptBuilder = chatClient.prompt()
            .system(buildSystemPrompt(timeContext));

        return promptBuilder.user(userSpec -> {
            userSpec.text(buildUserPrompt(description, images, timeContext));

            if (images != null && !images.isEmpty()) {
                images.forEach(image -> attachImageToSpec(userSpec, image));
            }
        }).call().entity(AiMealExtractionResponse.class);
    }

    private void attachImageToSpec(org.springframework.ai.chat.client.ChatClient.PromptUserSpec userSpec, MultipartFile image) {
        try {
            String mimeType = getMimeType(image);
            byte[] imageBytes = image.getBytes();
            userSpec.media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes));
        } catch (IOException e) {
            log.warn("Failed to read image: {}", image.getOriginalFilename(), e);
        }
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

    private String buildSystemPrompt(MealTimeContext timeContext) {
        String timeInfo = buildTimeContextInfo(timeContext);

        return timeInfo + """
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
            9. ALWAYS provide detailed description with component breakdown
            10. Report mealTypeConfidence (0.0 to 1.0) - your confidence in the meal type determination

            DESCRIPTION FIELD RULES:
            - Write in Polish language
            - List each identified component/ingredient separately
            - For each component include: estimated weight/portion and nutritional contribution
            - Show how total values were calculated
            - Format as bullet points using newline characters
            - Use \\n for newlines in JSON string (NOT string concatenation with +)
            - Example: "Kurczak - skladniki:\\n• Ryz (~150g): ~200 kcal\\n• Kurczak (~200g): ~350 kcal"

            CLARIFYING QUESTIONS RULES:
            - Generate 0-3 questions maximum
            - Questions should help improve accuracy of nutritional estimates
            - Use Polish language for questions
            - Include which fields the answer would affect
            - Common scenarios requiring questions:
              * Unclear portion size (mala/srednia/duza porcja)
              * Multiple preparation methods possible (smazony/pieczony/gotowany)
              * Missing key ingredients (z sosem/bez sosu)
              * Ambiguous meal type (if mealTypeConfidence < 0.5, add question about meal type)

            QUESTION TYPES:
            - SINGLE_CHOICE: Provide 2-4 options
            - YES_NO: Boolean question
            - FREE_TEXT: Open answer (use sparingly)

            MEAL TYPE DETERMINATION RULES (mealType):

            Priority 1 - Context from description/photo:
            - If user explicitly specifies type (e.g., "breakfast", "dinner", "lunch"), use it

            Priority 2 - Time of day + today's meals context:
            - 06:00-10:00 (morning):
              * If NO breakfast yet -> BREAKFAST
              * If breakfast EXISTS and small meal -> BRUNCH (second breakfast)
              * If large meal -> LUNCH
            - 10:00-12:00 (late morning):
              * If NO breakfast -> BREAKFAST (late breakfast)
              * Small meal -> BRUNCH
              * Large meal -> LUNCH
            - 12:00-15:00 (midday):
              * Large meal -> LUNCH
              * Small meal -> SNACK
            - 15:00-17:00 (afternoon):
              * Usually -> SNACK
              * Large meal without lunch -> LUNCH
            - 17:00-21:00 (evening):
              * Large meal -> DINNER
              * Small meal -> SNACK
            - 21:00-06:00 (night):
              * -> SNACK

            Priority 3 - Type of food:
            - Beverages (coffee, tea, smoothie, juice) -> DRINK
            - Sweets, cakes, ice cream, cookies -> DESSERT
            - Large savory main dishes (meat/fish + vegetables/carbs, >400 kcal estimated) -> LUNCH
            - Small fruits, single items (banana, apple, yogurt) -> SNACK

            Priority 4 - Default when NO time context available:
            - Full meals with protein source (chicken, beef, fish, eggs with sides) -> LUNCH
            - Light items or single foods without time context -> SNACK
            - NEVER default to BREAKFAST without explicit morning time context or breakfast-specific foods (cereal, eggs benedict, pancakes)

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
              "mealTypeConfidence": number (0.0-1.0),
              "title": "dish name in Polish" or null,
              "description": "detailed component breakdown in Polish" or null,
              "mealType": "BREAKFAST" | "BRUNCH" | "LUNCH" | "DINNER" | "SNACK" | "DESSERT" | "DRINK",
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

    private String buildTimeContextInfo(MealTimeContext timeContext) {
        if (timeContext == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT TIME: ").append(timeContext.formatCurrentTime()).append("\n");
        sb.append("CURRENT DATE: ").append(timeContext.currentDate()).append("\n\n");
        sb.append("TODAY'S MEALS:\n");
        sb.append(timeContext.formatMealsForPrompt()).append("\n\n");

        return sb.toString();
    }

    private String sanitizeUserInput(String input) {
        if (input == null) {
            return null;
        }
        return guardrailFacade.sanitizeOnly(input, GuardrailProfile.IMAGE_IMPORT);
    }

    private String buildUserPrompt(String description, List<MultipartFile> images, MealTimeContext timeContext) {
        StringBuilder prompt = new StringBuilder("Analyze this meal.\n");

        if (description != null && !description.isBlank()) {
            var sanitizedDescription = sanitizeUserInput(description);
            prompt.append("User-provided description (untrusted user input): ").append(sanitizedDescription).append("\n");
        }

        if (images != null && !images.isEmpty()) {
            prompt.append("Photos attached showing the meal (").append(images.size()).append(" image(s)).\n");
        }

        if (timeContext != null) {
            prompt.append("\nCurrent time context for meal type inference:\n");
            prompt.append("- Time: ").append(timeContext.formatCurrentTime()).append("\n");
            prompt.append("- Date: ").append(timeContext.currentDate()).append("\n");
            if (timeContext.hasBreakfast()) {
                prompt.append("- User already had breakfast today\n");
            }
            if (timeContext.hasBrunch()) {
                prompt.append("- User already had brunch today\n");
            }
            if (timeContext.hasLunch()) {
                prompt.append("- User already had lunch today\n");
            }
            if (timeContext.hasDinner()) {
                prompt.append("- User already had dinner today\n");
            }
        }

        prompt.append("\nExtract nutritional information and return as JSON.\n");
        prompt.append("Include mealTypeConfidence field indicating your confidence in the meal type determination.\n");
        prompt.append("If this is not food-related, return:\n");
        prompt.append("{\"isMeal\": false, \"confidence\": X, \"mealTypeConfidence\": 0, \"validationError\": \"Reason\"}");

        return prompt.toString();
    }

    private ExtractedMealData transformToExtractedMealData(AiMealExtractionResponse response) {
        if (response == null) {
            throw new MealExtractionException("AI returned null response");
        }

        double confidence = response.confidence();

        if (!response.isMeal()) {
            String error = response.validationError() != null
                    ? response.validationError()
                    : "Not a meal image/description";
            return ExtractedMealData.invalid(error, confidence);
        }

        Instant occurredAt = parseOccurredAt(response.occurredAt());

        String title = response.title();
        String description = response.description();
        String mealType = response.mealType();
        Integer caloriesKcal = validateNutritionalValue(response.caloriesKcal(), "calories", MAX_CALORIES);
        Integer proteinGrams = validateNutritionalValue(response.proteinGrams(), "protein", MAX_PROTEIN_GRAMS);
        Integer fatGrams = validateNutritionalValue(response.fatGrams(), "fat", MAX_FAT_GRAMS);
        Integer carbohydratesGrams = validateNutritionalValue(response.carbohydratesGrams(), "carbs", MAX_CARBS_GRAMS);
        String healthRating = response.healthRating();

        if (title == null || mealType == null) {
            return ExtractedMealData.invalid("Missing required fields (title or mealType)", confidence);
        }

        if (caloriesKcal == null || proteinGrams == null || fatGrams == null || carbohydratesGrams == null) {
            return ExtractedMealData.invalid("Missing or invalid nutritional values", confidence);
        }

        if (healthRating == null) {
            healthRating = "NEUTRAL";
        }

        double mealTypeConfidence = response.mealTypeConfidence() != null ? response.mealTypeConfidence() : 1.0;
        List<ClarifyingQuestion> questions = transformQuestions(response.questions());

        if (mealTypeConfidence < MEAL_TYPE_CONFIDENCE_THRESHOLD && !hasMealTypeQuestion(questions)) {
            questions = new ArrayList<>(questions);
            questions.add(createMealTypeQuestion());
            log.debug("Added meal type clarifying question due to low confidence: {}", mealTypeConfidence);
        }

        return ExtractedMealData.validWithQuestions(
            occurredAt, title, description, mealType,
            caloriesKcal, proteinGrams, fatGrams, carbohydratesGrams,
            healthRating, confidence, questions
        );
    }

    private List<ClarifyingQuestion> transformQuestions(List<AiMealExtractionResponse.AiQuestion> aiQuestions) {
        if (aiQuestions == null || aiQuestions.isEmpty()) {
            return List.of();
        }

        return aiQuestions.stream()
            .map(this::transformQuestion)
            .flatMap(Optional::stream)
            .toList();
    }

    private Optional<ClarifyingQuestion> transformQuestion(AiMealExtractionResponse.AiQuestion aiQuestion) {
        if (aiQuestion == null) {
            return Optional.empty();
        }

        String questionId = aiQuestion.questionId();
        String questionText = aiQuestion.questionText();
        String questionTypeStr = aiQuestion.questionType();

        if (questionId == null || questionText == null || questionTypeStr == null) {
            return Optional.empty();
        }

        ClarifyingQuestion.QuestionType questionType = parseQuestionType(questionTypeStr);
        List<String> options = aiQuestion.options() != null ? aiQuestion.options() : List.of();
        List<String> affectedFields = aiQuestion.affectedFields() != null ? aiQuestion.affectedFields() : List.of();

        return Optional.of(new ClarifyingQuestion(
            questionId, questionText, questionType, options, affectedFields
        ));
    }

    private ClarifyingQuestion.QuestionType parseQuestionType(String questionTypeStr) {
        try {
            return ClarifyingQuestion.QuestionType.valueOf(questionTypeStr);
        } catch (IllegalArgumentException e) {
            log.debug("Unknown question type: {}", questionTypeStr);
            return ClarifyingQuestion.QuestionType.FREE_TEXT;
        }
    }

    private boolean hasMealTypeQuestion(List<ClarifyingQuestion> questions) {
        return questions.stream()
            .anyMatch(q -> "meal_type".equals(q.questionId()) ||
                          q.affectedFields().contains("mealType"));
    }

    private ClarifyingQuestion createMealTypeQuestion() {
        return new ClarifyingQuestion(
            "meal_type",
            "What type of meal is this?",
            ClarifyingQuestion.QuestionType.SINGLE_CHOICE,
            List.of("BREAKFAST", "BRUNCH", "LUNCH", "DINNER", "SNACK", "DESSERT", "DRINK"),
            List.of("mealType")
        );
    }

    private Instant parseOccurredAt(String occurredAtStr) {
        if (occurredAtStr == null || occurredAtStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(occurredAtStr);
        } catch (Exception e) {
            log.debug("Could not parse occurredAt: {}", occurredAtStr);
            return null;
        }
    }

    private Integer validateNutritionalValue(Integer value, String fieldName, int maxValue) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > maxValue) {
            log.warn("Invalid {} from AI: {} (expected 0-{})", fieldName, value, maxValue);
            return null;
        }
        return value;
    }

    ExtractedMealData reAnalyzeWithContext(
            String originalDescription,
            ExtractedMealData previousExtraction,
            List<QuestionAnswer> answers,
            String userFeedback
    ) {
        log.info("Re-analyzing meal with context. Has answers: {}, has feedback: {}",
            answers != null && !answers.isEmpty(),
            userFeedback != null && !userFeedback.isBlank());

        String reAnalysisPrompt = buildReAnalysisPrompt(originalDescription, previousExtraction, answers, userFeedback);

        try {
            AiMealExtractionResponse response = chatClient.prompt()
                .system(buildReAnalysisSystemPrompt())
                .user(reAnalysisPrompt)
                .call()
                .entity(AiMealExtractionResponse.class);

            return transformToExtractedMealData(response);
        } catch (Exception e) {
            log.error("Re-analysis failed: {}", e.getMessage(), e);
            throw new MealExtractionException("Failed to re-analyze meal: " + e.getMessage(), e);
        }
    }

    private String buildReAnalysisSystemPrompt() {
        return """
            You are a nutrition expert correcting a previous meal analysis based on user feedback.

            Your task is to adjust the nutritional estimates based on the new information provided.

            IMPORTANT RULES:
            1. Return ONLY valid JSON without any additional text
            2. All numeric fields must be numbers (not strings)
            3. Adjust estimates based on user corrections
            4. If user says portion was larger/smaller, scale all values accordingly
            5. If user corrects an ingredient, recalculate affected nutrients
            6. If user specifies a different time, update occurredAt
            7. Update the description to reflect the corrected analysis
            8. Do NOT generate new questions - the user has already provided clarification

            RESPONSE FORMAT (same as initial analysis):
            {
              "isMeal": true,
              "confidence": number (0.0-1.0),
              "title": "dish name in Polish",
              "description": "corrected component breakdown in Polish",
              "mealType": "LUNCH" | "DINNER" | etc.,
              "occurredAt": "ISO-8601 datetime" or null,
              "caloriesKcal": number,
              "proteinGrams": number,
              "fatGrams": number,
              "carbohydratesGrams": number,
              "healthRating": "HEALTHY" | "NEUTRAL" | etc.,
              "questions": []
            }

            ADJUSTMENT GUIDELINES:
            - Small portion: multiply by 0.7
            - Large portion: multiply by 1.4
            - Fried vs baked: fried adds ~30% more fat and calories
            - With sauce vs without: sauce adds ~50-100 kcal
            - If user says "it was X not Y", replace the ingredient completely
            """;
    }

    private String buildReAnalysisPrompt(
            String originalDescription,
            ExtractedMealData previousExtraction,
            List<QuestionAnswer> answers,
            String userFeedback
    ) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("POPRZEDNIA ANALIZA POSILKU:\n");
        prompt.append("- Tytul: ").append(previousExtraction.title()).append("\n");
        if (previousExtraction.description() != null) {
            prompt.append("- Opis: ").append(previousExtraction.description()).append("\n");
        }
        prompt.append("- Kalorie: ").append(previousExtraction.caloriesKcal()).append(" kcal\n");
        prompt.append("- Bialko: ").append(previousExtraction.proteinGrams()).append("g\n");
        prompt.append("- Tluszcz: ").append(previousExtraction.fatGrams()).append("g\n");
        prompt.append("- Weglowodany: ").append(previousExtraction.carbohydratesGrams()).append("g\n");
        prompt.append("- Ocena zdrowotna: ").append(previousExtraction.healthRating()).append("\n");
        prompt.append("- Typ posilku: ").append(previousExtraction.mealType()).append("\n");
        if (previousExtraction.occurredAt() != null) {
            prompt.append("- Data/czas: ").append(previousExtraction.occurredAt()).append("\n");
        }
        prompt.append("\n");

        if (originalDescription != null && !originalDescription.isBlank()) {
            prompt.append("ORYGINALNY OPIS UZYTKOWNIKA:\n");
            prompt.append(originalDescription).append("\n\n");
        }

        if (answers != null && !answers.isEmpty()) {
            prompt.append("ODPOWIEDZI NA PYTANIA:\n");
            answers.forEach(answer ->
                prompt.append("- ").append(answer.questionId()).append(": ").append(answer.answer()).append("\n")
            );
            prompt.append("\n");
        }

        if (userFeedback != null && !userFeedback.isBlank()) {
            prompt.append("KOMENTARZ/POPRAWKA UZYTKOWNIKA:\n");
            prompt.append(userFeedback).append("\n\n");
        }

        prompt.append("Skoryguj analize na podstawie powyzszych informacji. ");
        prompt.append("Zaktualizuj wartosci odzywcze i opis zgodnie z poprawkami uzytkownika. ");
        prompt.append("Zwroc pelny JSON z zaktualizowanymi wartosciami.");

        return prompt.toString();
    }
}
