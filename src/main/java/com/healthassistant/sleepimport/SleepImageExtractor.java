package com.healthassistant.sleepimport;

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
    private static final Map<String, Integer> POLISH_MONTHS = Map.ofEntries(
            Map.entry("sty", 1), Map.entry("lut", 2), Map.entry("mar", 3),
            Map.entry("kwi", 4), Map.entry("maj", 5), Map.entry("cze", 6),
            Map.entry("lip", 7), Map.entry("sie", 8), Map.entry("wrz", 9),
            Map.entry("paź", 10), Map.entry("paz", 10), Map.entry("lis", 11), Map.entry("gru", 12)
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    ExtractedSleepData extract(MultipartFile image, int year) throws SleepExtractionException {
        try {
            byte[] imageBytes = image.getBytes();
            String contentType = image.getContentType();
            final String mimeType = (contentType == null || contentType.equals("application/octet-stream"))
                    ? "image/jpeg"
                    : contentType;

            log.info("Extracting sleep data from image: {} bytes, type: {}, year: {}", imageBytes.length, mimeType, year);

            String response = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .user(userSpec -> userSpec
                            .text(buildUserPrompt())
                            .media(MimeType.valueOf(mimeType), new ByteArrayResource(imageBytes))
                    )
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new SleepExtractionException("AI returned empty response");
            }

            log.debug("AI response: {}", response);

            return parseExtractionResponse(response, year);

        } catch (IOException e) {
            throw new SleepExtractionException("Failed to read image: " + e.getMessage(), e);
        } catch (SleepExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI extraction failed", e);
            throw new SleepExtractionException("AI extraction failed: " + e.getMessage(), e);
        }
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
            Extract all sleep data including phases and return as JSON.

            If this is not a sleep summary screenshot, return:
            {"isSleepScreenshot": false, "confidence": 0.9, "validationError": "Reason for rejection"}
            """;
    }

    private ExtractedSleepData parseExtractionResponse(String response, int year) throws SleepExtractionException {
        try {
            String cleanedResponse = cleanJsonResponse(response);
            JsonNode root = objectMapper.readTree(cleanedResponse);

            boolean isSleepScreenshot = root.path("isSleepScreenshot").asBoolean(false);
            double confidence = root.path("confidence").asDouble(0.0);

            if (!isSleepScreenshot) {
                String error = root.path("validationError").asText("Not a sleep screenshot");
                return ExtractedSleepData.invalid(error, confidence);
            }

            LocalDate sleepDate = parseSleepDate(root.path("sleepDate").asText(null), year);
            String sleepStartStr = root.path("sleepStart").asText(null);
            String wakeTimeStr = root.path("wakeTime").asText(null);

            if (sleepDate == null || sleepStartStr == null || wakeTimeStr == null) {
                return ExtractedSleepData.invalid("Missing required date/time fields", confidence);
            }

            Instant sleepStart = parseLocalTimeToInstant(sleepDate, sleepStartStr, true);
            Instant sleepEnd = parseLocalTimeToInstant(sleepDate, wakeTimeStr, false);

            if (!sleepStart.isBefore(sleepEnd)) {
                sleepStart = parseLocalTimeToInstant(sleepDate.minusDays(1), sleepStartStr, true);
            }

            Integer totalSleepMinutes = parseNullableInt(root.path("totalSleepMinutes"));
            Integer sleepScore = parseNullableInt(root.path("sleepScore"));
            String qualityLabel = root.path("qualityLabel").asText(null);

            JsonNode phasesNode = root.path("phases");
            ExtractedSleepData.Phases phases = new ExtractedSleepData.Phases(
                    parseNullableInt(phasesNode.path("lightSleepMinutes")),
                    parseNullableInt(phasesNode.path("deepSleepMinutes")),
                    parseNullableInt(phasesNode.path("remSleepMinutes")),
                    parseNullableInt(phasesNode.path("awakeMinutes"))
            );

            log.info("Successfully extracted sleep data: date={}, duration={}min, score={}, confidence={}",
                    sleepDate, totalSleepMinutes, sleepScore, confidence);

            return ExtractedSleepData.valid(
                    sleepDate, sleepStart, sleepEnd, totalSleepMinutes,
                    sleepScore, phases, qualityLabel, confidence
            );

        } catch (JsonProcessingException e) {
            throw new SleepExtractionException("Failed to parse AI response as JSON: " + e.getMessage(), e);
        }
    }

    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
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
            } catch (Exception e) {
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

    private Integer parseNullableInt(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
