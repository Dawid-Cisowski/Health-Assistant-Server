package com.healthassistant.sleepimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.guardrails.api.GuardrailFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class SleepImageExtractor {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final int MAX_MINUTES_IN_DAY = 1440;
    private static final int MAX_SLEEP_SCORE = 100;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.7;
    private static final Map<String, Integer> POLISH_MONTHS = Map.ofEntries(
            Map.entry("sty", 1), Map.entry("lut", 2), Map.entry("mar", 3),
            Map.entry("kwi", 4), Map.entry("maj", 5), Map.entry("cze", 6),
            Map.entry("lip", 7), Map.entry("sie", 8), Map.entry("wrz", 9),
            Map.entry("paź", 10), Map.entry("paz", 10), Map.entry("lis", 11), Map.entry("gru", 12)
    );

    private final ChatClient chatClient;
    @SuppressWarnings("unused") // Reserved for future image content moderation
    private final GuardrailFacade guardrailFacade;
    private final ObjectMapper objectMapper;

    ExtractedSleepData extract(MultipartFile image, int year) throws SleepExtractionException {
        try {
            byte[] imageBytes = image.getBytes();
            String contentType = image.getContentType();
            final String mimeType = (contentType == null || contentType.equals("application/octet-stream"))
                    ? "image/jpeg"
                    : contentType;

            log.info("Extracting sleep data from image: {} bytes, type: {}, year: {}", imageBytes.length, mimeType, year);

            ChatResponse chatResponse = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .user(userSpec -> userSpec
                            .text(buildUserPrompt())
                            .media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes))
                    )
                    .call()
                    .chatResponse();

            String jsonContent = chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : null;

            if (jsonContent == null || jsonContent.isBlank()) {
                throw new SleepExtractionException("AI returned empty response");
            }

            // Clean JSON content (remove markdown code blocks if present)
            jsonContent = cleanJsonResponse(jsonContent);

            AiSleepExtractionResponse response;
            try {
                response = objectMapper.readValue(jsonContent, AiSleepExtractionResponse.class);
            } catch (Exception e) {
                log.error("Failed to parse AI response as JSON: {}", jsonContent, e);
                throw new SleepExtractionException("Failed to parse AI response: " + e.getMessage(), e);
            }

            if (response == null) {
                throw new SleepExtractionException("AI returned null response");
            }

            Long promptTokens = null;
            Long completionTokens = null;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
                completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
            }

            log.debug("AI call completed: tokens {}/{}", promptTokens, completionTokens);
            return transformToExtractedSleepData(response, year, promptTokens, completionTokens);

        } catch (IOException e) {
            log.error("Failed to read image file", e);
            throw new SleepExtractionException("Failed to read image file", e);
        } catch (SleepExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI extraction failed", e);
            throw new SleepExtractionException("Failed to extract sleep data from image", e);
        }
    }

    private String cleanJsonResponse(String content) {
        if (content == null) return null;
        String cleaned = content.trim();
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

    private String buildSystemPrompt() {
        return """
            You are an expert at analyzing sleep tracking app screenshots, particularly from ohealth/O-Health app.

            Your task is to extract sleep data from the screenshot and return it as JSON.

            IMPORTANT RULES:
            1. Return ONLY valid JSON without any additional text or markdown
            2. If the image is NOT a sleep summary screenshot, return JSON with "isSleepScreenshot": false
            3. All numeric fields must be numbers (not strings)
            4. Time values should be in 24-hour format (HH:MM)
            5. Date: extract ONLY day and month as "DD-MM" (e.g., "25-12" for December 25). Do NOT guess the year.
            6. Report your confidence level (0.0 to 1.0)
            7. Sleep phases are shown as a visual chart - estimate proportions carefully

            POLISH TERMINOLOGY (the app UI is in Polish):
            - "Sen" = Sleep (screen title)
            - "Pora snu" = Sleep start time (bedtime)
            - "Budzenie" = Wake time
            - "Głęboki sen" = Deep sleep
            - "Płytki sen" = Light sleep
            - "REM" = REM sleep
            - "Jawa" = Awake periods
            - "Normalny" = Normal (sleep quality)
            - "Ocena snu" = Sleep score
            - "Fazy snu" = Sleep phases
            - "Informacje ogólne" = General information

            DATE FORMAT RECOGNITION:
            - Format: "DD mmm, Ddd" (e.g., "25 gru, Czw" = December 25, Thursday)
            - Month abbreviations: sty (Jan), lut (Feb), mar (Mar), kwi (Apr), maj (May), cze (Jun),
              lip (Jul), sie (Aug), wrz (Sep), paź/paz (Oct), lis (Nov), gru (Dec)
            - Weekday abbreviations: Pon, Wt, Śr, Czw, Pt, Sob, Niedz

            PHASE ESTIMATION FROM CHART:
            - Look at the sleep chart (hypnogram) showing colored bands for each phase
            - Estimate the proportion of each phase relative to total sleep duration
            - Deep sleep (Głęboki sen) is typically shown in darker color at the bottom
            - Light sleep (Płytki sen) is shown in lighter color
            - REM is shown in a distinct color (often blue/purple)
            - Awake periods (Jawa) are shown as gaps or different color at the top
            - Calculate minutes based on proportions of total sleep time

            RESPONSE FORMAT (JSON Schema):
            {
              "isSleepScreenshot": boolean,
              "confidence": number (0.0-1.0),
              "sleepDate": "DD-MM" (day-month only, e.g., "25-12") or null,
              "sleepStart": "HH:MM" (local time, 24h format) or null,
              "wakeTime": "HH:MM" (local time, 24h format) or null,
              "totalSleepMinutes": number or null,
              "sleepScore": number (0-100) or null,
              "phases": {
                "lightSleepMinutes": number or null,
                "deepSleepMinutes": number or null,
                "remSleepMinutes": number or null,
                "awakeMinutes": number or null
              },
              "qualityLabel": "string" or null,
              "validationError": "error description" or null
            }

            IMPORTANT CALCULATION NOTES:
            - Total sleep time is usually shown as "Xh Ymin" (e.g., "7h 7min" = 427 minutes)
            - Sleep score is usually shown as a number 0-100 in a circle
            - If exact phase minutes aren't visible, estimate from chart proportions
            - Phases should roughly sum to total sleep time (allow some tolerance for awake periods)
            """;
    }

    private String buildUserPrompt() {
        return """
            Analyze this sleep tracking app screenshot from ohealth.
            Extract all sleep data including phases and return as JSON according to the response format.

            If this is not a sleep summary screenshot, set isSleepScreenshot to false and include the reason in validationError.
            """;
    }

    private ExtractedSleepData transformToExtractedSleepData(AiSleepExtractionResponse response, int year,
                                                              Long promptTokens, Long completionTokens) {
        double confidence = response.confidence();

        if (confidence < 0.0 || confidence > 1.0) {
            log.warn("AI returned invalid confidence: {}", confidence);
            confidence = 0.0;
        }

        if (!response.isSleepScreenshot()) {
            String error = response.validationError() != null
                    ? response.validationError()
                    : "Not a sleep screenshot";
            return ExtractedSleepData.invalid(error, confidence);
        }

        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            return ExtractedSleepData.invalid(
                    String.format("AI confidence too low: %.2f (minimum: %.1f)", confidence, MIN_CONFIDENCE_THRESHOLD),
                    confidence
            );
        }

        LocalDate sleepDate = parseSleepDate(response.sleepDate(), year);
        String sleepStartStr = response.sleepStart();
        String wakeTimeStr = response.wakeTime();

        if (sleepDate == null || sleepStartStr == null || wakeTimeStr == null) {
            return ExtractedSleepData.invalid("Missing required date/time fields", confidence);
        }

        Instant sleepStart = parseLocalTimeToInstant(sleepDate, sleepStartStr, true);
        Instant sleepEnd = parseLocalTimeToInstant(sleepDate, wakeTimeStr, false);

        if (!sleepStart.isBefore(sleepEnd)) {
            sleepStart = parseLocalTimeToInstant(sleepDate.minusDays(1), sleepStartStr, true);
        }

        Integer totalSleepMinutes = response.totalSleepMinutes();
        Integer sleepScore = response.sleepScore();
        String qualityLabel = response.qualityLabel();

        AiSleepExtractionResponse.AiPhases aiPhases = response.phases();
        Integer lightSleepMinutes = aiPhases != null ? aiPhases.lightSleepMinutes() : null;
        Integer deepSleepMinutes = aiPhases != null ? aiPhases.deepSleepMinutes() : null;
        Integer remSleepMinutes = aiPhases != null ? aiPhases.remSleepMinutes() : null;
        Integer awakeMinutes = aiPhases != null ? aiPhases.awakeMinutes() : null;

        String rangeValidationError = validateNumericRanges(
                totalSleepMinutes, sleepScore, lightSleepMinutes, deepSleepMinutes, remSleepMinutes, awakeMinutes
        );
        if (rangeValidationError != null) {
            return ExtractedSleepData.invalid(rangeValidationError, confidence);
        }

        ExtractedSleepData.Phases phases = new ExtractedSleepData.Phases(
                lightSleepMinutes, deepSleepMinutes, remSleepMinutes, awakeMinutes
        );

        log.info("Successfully extracted sleep data: date={}, duration={}min, score={}, confidence={}, tokens={}/{}",
                sleepDate, totalSleepMinutes, sleepScore, confidence, promptTokens, completionTokens);

        return ExtractedSleepData.validWithTokens(
                sleepDate, sleepStart, sleepEnd, totalSleepMinutes,
                sleepScore, phases, qualityLabel, confidence, promptTokens, completionTokens
        );
    }

    private LocalDate parseSleepDate(String dateStr, int year) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now(POLAND_ZONE).withYear(year);
        }

        // Try DD-MM format (expected from AI)
        if (dateStr.matches("\\d{1,2}-\\d{1,2}")) {
            try {
                String[] parts = dateStr.split("-");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                return LocalDate.of(year, month, day);
            } catch (NumberFormatException | java.time.DateTimeException e) {
                log.warn("Could not parse DD-MM date: {}", dateStr);
            }
        }

        // Try full ISO format (backward compatibility)
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            // Try Polish format as fallback
            return parsePolishDate(dateStr, year);
        }
    }

    private LocalDate parsePolishDate(String dateStr, int year) {
        try {
            String[] parts = dateStr.toLowerCase(java.util.Locale.ROOT).split("\\s+");
            if (parts.length >= 2) {
                int day = Integer.parseInt(parts[0]);
                String monthStr = parts[1].replace(",", "");
                Integer month = POLISH_MONTHS.get(monthStr);
                if (month != null) {
                    return LocalDate.of(year, month, day);
                }
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse Polish date: {}", dateStr);
        }
        return LocalDate.now(POLAND_ZONE).withYear(year);
    }

    private Instant parseLocalTimeToInstant(LocalDate date, String timeStr, boolean isSleepStart) {
        try {
            LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            ZonedDateTime zdt = ZonedDateTime.of(date, time, POLAND_ZONE);
            return zdt.toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse time: {}, using default", timeStr);
            LocalTime defaultTime = isSleepStart ? LocalTime.of(23, 0) : LocalTime.of(7, 0);
            return ZonedDateTime.of(date, defaultTime, POLAND_ZONE).toInstant();
        }
    }

    private String validateNumericRanges(
            Integer totalSleepMinutes,
            Integer sleepScore,
            Integer lightSleepMinutes,
            Integer deepSleepMinutes,
            Integer remSleepMinutes,
            Integer awakeMinutes
    ) {
        if (totalSleepMinutes != null && (totalSleepMinutes < 0 || totalSleepMinutes > MAX_MINUTES_IN_DAY)) {
            return String.format("Invalid totalSleepMinutes: %d (must be 0-%d)", totalSleepMinutes, MAX_MINUTES_IN_DAY);
        }
        if (sleepScore != null && (sleepScore < 0 || sleepScore > MAX_SLEEP_SCORE)) {
            return String.format("Invalid sleepScore: %d (must be 0-%d)", sleepScore, MAX_SLEEP_SCORE);
        }
        if (lightSleepMinutes != null && (lightSleepMinutes < 0 || lightSleepMinutes > MAX_MINUTES_IN_DAY)) {
            return String.format("Invalid lightSleepMinutes: %d (must be 0-%d)", lightSleepMinutes, MAX_MINUTES_IN_DAY);
        }
        if (deepSleepMinutes != null && (deepSleepMinutes < 0 || deepSleepMinutes > MAX_MINUTES_IN_DAY)) {
            return String.format("Invalid deepSleepMinutes: %d (must be 0-%d)", deepSleepMinutes, MAX_MINUTES_IN_DAY);
        }
        if (remSleepMinutes != null && (remSleepMinutes < 0 || remSleepMinutes > MAX_MINUTES_IN_DAY)) {
            return String.format("Invalid remSleepMinutes: %d (must be 0-%d)", remSleepMinutes, MAX_MINUTES_IN_DAY);
        }
        if (awakeMinutes != null && (awakeMinutes < 0 || awakeMinutes > MAX_MINUTES_IN_DAY)) {
            return String.format("Invalid awakeMinutes: %d (must be 0-%d)", awakeMinutes, MAX_MINUTES_IN_DAY);
        }
        return null;
    }
}
