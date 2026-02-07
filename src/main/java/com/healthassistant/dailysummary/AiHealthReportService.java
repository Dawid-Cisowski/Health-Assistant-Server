package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.AiHealthReportResponse;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AiHealthReportService {

    private static final int MAX_AI_INPUT_LENGTH = 50_000;
    private static final int MAX_WORKOUT_NOTE_LENGTH = 500;
    private static final Set<String> PROMPT_INJECTION_PATTERNS = Set.of(
            "ignore previous", "ignore all previous", "system prompt",
            "you are now", "new instructions", "disregard",
            "forget your instructions", "override"
    );

    private final DailySummaryFacade dailySummaryFacade;
    private final DailySummaryJpaRepository repository;
    private final ChatClient chatClient;
    private final GuardrailFacade guardrailFacade;
    private final AiMetricsRecorder aiMetrics;

    private static final String DAILY_REPORT_SYSTEM_PROMPT = """
        You are a health assistant that generates detailed daily health reports.

        RULES:
        - Write in Polish
        - Use Markdown formatting with ## headers, **bold** for key metrics, bullet lists
        - Be informative but concise
        - Include specific numbers and metrics
        - Add brief recommendations at the end
        - DO NOT invent data that is not provided

        REPORT STRUCTURE (use only sections with available data):
        ## Podsumowanie
        2-3 sentences overview of the day.

        ## Aktywność
        - Steps, active minutes, calories burned, distance
        - Comparison to daily goals (10000 steps, 5h activity, 2500kcal)

        ## Sen
        - Total sleep time, comparison to 7h goal

        ## Odżywianie
        - Total calories, macros (protein/fat/carbs)
        - Number of meals, health ratings

        ## Trening
        - Number of workouts, types, notes

        ## Tętno
        - Resting HR, average HR, max HR

        ## Wnioski
        - 2-3 bullet points with key takeaways or recommendations
        """;

    private static final String RANGE_REPORT_SYSTEM_PROMPT = """
        You are a health assistant that generates detailed health reports for a date range (week or month).

        RULES:
        - Write in Polish
        - Use Markdown formatting with ## headers, **bold** for key metrics, bullet lists
        - Focus on trends and averages, not individual days
        - Be informative but concise
        - Include specific numbers and metrics
        - Add brief recommendations at the end
        - DO NOT invent data that is not provided

        REPORT STRUCTURE (use only sections with available data):
        ## Podsumowanie
        2-3 sentences overview of the period.

        ## Aktywność
        - Total and average steps, active minutes, calories burned, distance
        - Days meeting goals

        ## Sen
        - Average sleep duration, days with sleep data
        - Comparison to 7h goal

        ## Odżywianie
        - Average daily calories and macros
        - Average meals per day

        ## Treningi
        - Total workouts, workout frequency
        - List of workouts with dates

        ## Tętno
        - Average resting HR, average daily HR, max HR
        - Days with heart rate data

        ## Wnioski
        - 3-5 bullet points with key trends, achievements, and recommendations
        """;

    @Transactional
    AiHealthReportResponse generateDailyReport(String deviceId, LocalDate date) {
        log.info("Generating AI daily report for device {} date: {}", maskDeviceId(deviceId), date);
        var sample = aiMetrics.startTimer();

        var entityOpt = repository.findByDeviceIdAndDate(deviceId, date);
        if (entityOpt.isEmpty()) {
            log.info("No daily summary entity found for device {} date: {}", maskDeviceId(deviceId), date);
            return AiHealthReportResponse.noData(date, date);
        }

        DailySummaryJpaEntity entity = entityOpt.get();

        if (isReportCacheValid(entity)) {
            log.info("Returning cached AI report for device {} date: {}", maskDeviceId(deviceId), date);
            aiMetrics.recordSummaryRequest("daily_report", sample, "success", true);
            return AiHealthReportResponse.daily(date, entity.getAiReport(), true);
        }

        try {
            return dailySummaryFacade.getDailySummary(deviceId, date)
                    .map(summary -> {
                        String dataContext = summary.toAiDataContext(this::sanitizeForPrompt);
                        AiResult result = callAi(DAILY_REPORT_SYSTEM_PROMPT,
                                "Generate a detailed health report for " + date + " based on this data:\n\n" + dataContext);
                        cacheReport(entity, result.content());
                        aiMetrics.recordSummaryTokens("daily_report", result.promptTokens(), result.completionTokens());
                        aiMetrics.recordSummaryRequest("daily_report", sample, "success", false);
                        return AiHealthReportResponse.daily(date, result.content(), true);
                    })
                    .orElse(AiHealthReportResponse.noData(date, date));
        } catch (Exception e) {
            aiMetrics.recordSummaryRequest("daily_report", sample, "error", false);
            aiMetrics.recordAiError("daily_report", "api_error");
            throw e;
        }
    }

    AiHealthReportResponse generateRangeReport(String deviceId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating AI range report for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);
        var sample = aiMetrics.startTimer();

        DailySummaryRangeSummaryResponse rangeSummary = dailySummaryFacade.getRangeSummary(deviceId, startDate, endDate);

        if (rangeSummary.daysWithData() == null || rangeSummary.daysWithData() == 0) {
            log.info("No data available for device {} range {} to {}", maskDeviceId(deviceId), startDate, endDate);
            return AiHealthReportResponse.noData(startDate, endDate);
        }

        try {
            String dataContext = rangeSummary.toAiDataContext(this::sanitizeForPrompt);
            AiResult result = callAi(RANGE_REPORT_SYSTEM_PROMPT,
                    "Generate a detailed health report for the period " + startDate + " to " + endDate + " based on this data:\n\n" + dataContext);

            aiMetrics.recordSummaryTokens("range_report", result.promptTokens(), result.completionTokens());
            aiMetrics.recordSummaryRequest("range_report", sample, "success", false);
            return AiHealthReportResponse.range(startDate, endDate, result.content(), true);
        } catch (Exception e) {
            aiMetrics.recordSummaryRequest("range_report", sample, "error", false);
            aiMetrics.recordAiError("range_report", "api_error");
            throw e;
        }
    }

    private boolean isReportCacheValid(DailySummaryJpaEntity entity) {
        if (entity.getAiReport() == null || entity.getAiReportGeneratedAt() == null) {
            return false;
        }
        if (entity.getLastEventAt() == null) {
            return true;
        }
        return !entity.getLastEventAt().isAfter(entity.getAiReportGeneratedAt());
    }

    private void cacheReport(DailySummaryJpaEntity entity, String report) {
        entity.cacheAiReport(report);
        repository.save(entity);
        log.info("Cached AI report for date: {}", entity.getDate());
    }

    private record AiResult(String content, Long promptTokens, Long completionTokens) { }

    private AiResult callAi(String systemPrompt, String userMessage) {
        String effectiveMessage = userMessage;
        if (effectiveMessage.length() > MAX_AI_INPUT_LENGTH) {
            log.warn("AI input truncated from {} to {} chars", effectiveMessage.length(), MAX_AI_INPUT_LENGTH);
            aiMetrics.recordSummaryInputTruncated();
            effectiveMessage = effectiveMessage.substring(0, MAX_AI_INPUT_LENGTH) + "\n\n[Data truncated]";
        }

        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(effectiveMessage)
                    .call()
                    .chatResponse();

            String content = chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : null;

            if (content == null || content.isBlank()) {
                throw new AiSummaryGenerationException("AI returned empty report");
            }

            Long promptTokens = null;
            Long completionTokens = null;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                promptTokens = usage.getPromptTokens();
                completionTokens = usage.getGenerationTokens();
            }

            log.info("AI report generated: {} chars", content.length());
            return new AiResult(content, promptTokens, completionTokens);
        } catch (AiSummaryGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate AI report", e);
            throw new AiSummaryGenerationException("Failed to generate AI report", e);
        }
    }

    private String sanitizeForPrompt(String input) {
        if (input == null) {
            return "";
        }
        String truncated = input.length() > MAX_WORKOUT_NOTE_LENGTH
                ? input.substring(0, MAX_WORKOUT_NOTE_LENGTH)
                : input;
        String lower = truncated.toLowerCase(Locale.ROOT);
        boolean hasInjection = PROMPT_INJECTION_PATTERNS.stream()
                .anyMatch(lower::contains);
        if (hasInjection) {
            log.warn("Prompt injection pattern detected in workout note, stripping");
            return "[filtered]";
        }
        return guardrailFacade.sanitizeOnly(truncated, GuardrailProfile.DATA_EXTRACTION);
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
