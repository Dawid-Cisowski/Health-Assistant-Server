package com.healthassistant.weightimport;

import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import com.healthassistant.guardrails.api.GuardrailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import static java.util.Locale.*;

@Component
@RequiredArgsConstructor
@Slf4j
class WeightImageExtractor {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.7;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOG_LENGTH = 100;

    private final ChatClient chatClient;
    private final GuardrailFacade guardrailFacade;

    ExtractedWeightData extract(List<MultipartFile> images) throws WeightExtractionException {
        try {
            log.info("Extracting weight data from {} images, total size: {} bytes",
                    images.size(),
                    images.stream().mapToLong(MultipartFile::getSize).sum());

            AiWeightExtractionResponse response = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .user(userSpec -> {
                        userSpec.text(buildUserPrompt());
                        images.forEach(image -> {
                            try {
                                byte[] imageBytes = image.getBytes();
                                String mimeType = resolveImageMimeType(image.getContentType());
                                userSpec.media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes));
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to read image bytes", e);
                            }
                        });
                    })
                    .call()
                    .entity(AiWeightExtractionResponse.class);

            if (response == null) {
                throw new WeightExtractionException("AI returned null response");
            }

            // Security check on text fields that could contain injection attempts
            validateResponseSecurity(response);

            return transformToExtractedWeightData(response);

        } catch (WeightExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI extraction failed", e);
            throw new WeightExtractionException("Failed to extract weight data from image", e);
        }
    }

    private String buildSystemPrompt() {
        return """
            You are an expert at analyzing smart scale app screenshots, particularly from Chinese/Polish body composition apps.

            Your task is to extract body composition data from the screenshot and return it as JSON.

            IMPORTANT RULES:
            1. Return ONLY valid JSON without any additional text or markdown
            2. If the image is NOT a weight/body composition screenshot, return JSON with "isWeightScreenshot": false
            3. All numeric fields must be numbers (not strings)
            4. Report your confidence level (0.0 to 1.0)
            5. Extract ALL metrics visible in the screenshot

            POLISH TERMINOLOGY (the app UI may be in Polish):
            - "Wynik" = Score (overall health score 0-100)
            - "Waga" = Weight (kg)
            - "BMI" = Body Mass Index
            - "BFR" / "Procent tkanki tluszczowej" = Body Fat Rate (%)
            - "Miesnie" / "Procent miesni" = Muscle percentage
            - "Nawodnienie" = Hydration (%)
            - "Masa kostna" = Bone mass (kg)
            - "BMR" / "Podstawowa przemiana materii" = Basal Metabolic Rate (kcal)
            - "Tluszcz trzewny" = Visceral fat (level 1-59)
            - "Tluszcz podskórny" = Subcutaneous fat (%)
            - "Poziom bialka" = Protein level (%)
            - "Wiek ciala" / "Wiek metaboliczny" = Body/Metabolic age (years)
            - "Standardowa waga" / "Idealna waga" = Ideal weight (kg)
            - "Kontrola wagi" = Weight control (kg difference, negative = need to lose)
            - "Tluszcz" (masa) / "Masa tluszczu" = Fat mass (kg)
            - "Waga bez tluszczu" / "Masa beztluszczowa" = Lean body mass (kg)
            - "Masa miesni" = Muscle mass (kg)
            - "Masa bialkowa" = Protein mass (kg)
            - "Typ ciala" = Body type (category)
            - "Stopien otylosci" = Obesity degree

            DATE/TIME FORMAT:
            - Look for date/time at the top of the screen in format: "YYYY-MM-DD HH:mm:ss" (e.g., "2026-01-12 07:29:41")
            - Extract the exact datetime shown

            RESPONSE FORMAT (JSON Schema):
            {
              "isWeightScreenshot": boolean,
              "confidence": number (0.0-1.0),
              "measurementDateTime": "YYYY-MM-DD HH:mm:ss" or null,
              "score": number (0-100) or null,
              "weightKg": number or null,
              "bmi": number or null,
              "bodyFatPercent": number or null,
              "musclePercent": number or null,
              "hydrationPercent": number or null,
              "boneMassKg": number or null,
              "bmrKcal": number or null,
              "visceralFatLevel": number (1-59) or null,
              "subcutaneousFatPercent": number or null,
              "proteinPercent": number or null,
              "metabolicAge": number or null,
              "idealWeightKg": number or null,
              "weightControlKg": number (can be negative) or null,
              "fatMassKg": number or null,
              "leanBodyMassKg": number or null,
              "muscleMassKg": number or null,
              "proteinMassKg": number or null,
              "bodyType": "string" or null,
              "validationError": "error description" or null
            }

            IMPORTANT:
            - weightKg is REQUIRED - if you can't find it, return isWeightScreenshot: false
            - Extract numeric values without units (e.g., "72.6kg" -> 72.6)
            - For percentage values, extract without % sign (e.g., "21.0%" -> 21.0)
            - For negative values (like weightControlKg), preserve the sign (e.g., "-5.4kg" -> -5.4)
            """;
    }

    private String buildUserPrompt() {
        return """
            Analyze the provided smart scale/body composition app screenshot(s).
            If multiple images are provided, they show the same measurement (scrolled view).
            Combine data from ALL images to extract the complete set of metrics.
            Extract all weight and body composition metrics visible and return as JSON according to the response format.

            If this is not a weight/body composition screenshot, set isWeightScreenshot to false and include the reason in validationError.
            """;
    }

    private void validateResponseSecurity(AiWeightExtractionResponse response) throws WeightExtractionException {
        if (containsSuspiciousPatterns(response.bodyType())) {
            log.warn("Security: Suspicious patterns detected in bodyType field");
            throw new WeightExtractionException("Security: Suspicious patterns detected in AI response");
        }
        if (containsSuspiciousPatterns(response.validationError())) {
            log.warn("Security: Suspicious patterns detected in validationError field");
            throw new WeightExtractionException("Security: Suspicious patterns detected in AI response");
        }
    }

    private ExtractedWeightData transformToExtractedWeightData(AiWeightExtractionResponse response) {
        double confidence = response.confidence();

        if (confidence < 0.0 || confidence > 1.0) {
            log.warn("AI returned invalid confidence: {}", confidence);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }

        if (!response.isWeightScreenshot()) {
            String error = response.validationError() != null
                    ? response.validationError()
                    : "Not a weight screenshot";
            return ExtractedWeightData.invalid(error, confidence);
        }

        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            return ExtractedWeightData.invalid(
                    String.format("AI confidence too low: %.2f (minimum: %.1f)", confidence, MIN_CONFIDENCE_THRESHOLD),
                    confidence
            );
        }

        BigDecimal weightKg = response.weightKg();
        if (weightKg == null) {
            return ExtractedWeightData.invalid("Weight (weightKg) is required but not found", confidence);
        }

        String measurementDateTimeStr = response.measurementDateTime();
        LocalDate measurementDate;
        Instant measuredAt;

        if (measurementDateTimeStr != null && !measurementDateTimeStr.isBlank()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(measurementDateTimeStr, DATE_TIME_FORMATTER);
                measurementDate = ldt.toLocalDate();
                measuredAt = ldt.atZone(POLAND_ZONE).toInstant();
            } catch (DateTimeParseException e) {
                log.warn("Could not parse measurement datetime: {}, using current time", measurementDateTimeStr);
                measurementDate = LocalDate.now(POLAND_ZONE);
                measuredAt = Instant.now();
            }
        } else {
            measurementDate = LocalDate.now(POLAND_ZONE);
            measuredAt = Instant.now();
        }

        String bodyType = sanitizeBodyType(response.bodyType());

        log.info("Successfully extracted weight data: date={}, weight={}kg, score={}, BMI={}, confidence={}",
                sanitizeForLog(measurementDate.toString()), weightKg, response.score(), response.bmi(), confidence);

        return ExtractedWeightData.valid(
                measurementDate, measuredAt, response.score(), weightKg, response.bmi(),
                response.bodyFatPercent(), response.musclePercent(), response.hydrationPercent(),
                response.boneMassKg(), response.bmrKcal(), response.visceralFatLevel(),
                response.subcutaneousFatPercent(), response.proteinPercent(), response.metabolicAge(),
                response.idealWeightKg(), response.weightControlKg(), response.fatMassKg(),
                response.leanBodyMassKg(), response.muscleMassKg(), response.proteinMassKg(),
                bodyType, confidence
        );
    }

    private boolean containsSuspiciousPatterns(String input) {
        if (input == null) {
            return false;
        }
        GuardrailResult result = guardrailFacade.validateText(input, GuardrailProfile.DATA_EXTRACTION);
        return result.blocked();
    }

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        String sanitized = input.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.length() > MAX_LOG_LENGTH) {
            return sanitized.substring(0, MAX_LOG_LENGTH) + "...";
        }
        return sanitized;
    }

    private String sanitizeBodyType(String bodyType) {
        if (bodyType == null) {
            return null;
        }
        // Validate body type for suspicious patterns
        if (containsSuspiciousPatterns(bodyType)) {
            log.warn("Suspicious bodyType detected, nullifying: {}", sanitizeForLog(bodyType));
            return null;
        }
        return guardrailFacade.sanitizeOnly(bodyType, GuardrailProfile.DATA_EXTRACTION);
    }

    private String resolveImageMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "image/jpeg";
        }
        if (contentType.contains("*")) {
            return "image/jpeg";
        }
        if (contentType.equals("application/octet-stream")) {
            return "image/jpeg";
        }
        return switch (contentType.toLowerCase(ROOT)) {
            case "image/jpeg", "image/jpg" -> "image/jpeg";
            case "image/png" -> "image/png";
            case "image/gif" -> "image/gif";
            case "image/webp" -> "image/webp";
            case "image/heic", "image/heif" -> "image/heic";
            default -> "image/jpeg";
        };
    }
}
