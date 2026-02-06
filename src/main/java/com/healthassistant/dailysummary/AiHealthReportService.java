package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.AiHealthReportResponse;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.guardrails.api.GuardrailFacade;
import com.healthassistant.guardrails.api.GuardrailProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
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

        var entityOpt = repository.findByDeviceIdAndDate(deviceId, date);
        if (entityOpt.isEmpty()) {
            log.info("No daily summary entity found for device {} date: {}", maskDeviceId(deviceId), date);
            return AiHealthReportResponse.noData(date, date);
        }

        DailySummaryJpaEntity entity = entityOpt.get();

        if (isReportCacheValid(entity)) {
            log.info("Returning cached AI report for device {} date: {}", maskDeviceId(deviceId), date);
            return AiHealthReportResponse.daily(date, entity.getAiReport(), true);
        }

        return dailySummaryFacade.getDailySummary(deviceId, date)
                .map(summary -> {
                    String dataContext = buildDailyDataContext(summary);
                    String report = callAi(DAILY_REPORT_SYSTEM_PROMPT,
                            "Generate a detailed health report for " + date + " based on this data:\n\n" + dataContext);
                    cacheReport(entity, report);
                    return AiHealthReportResponse.daily(date, report, true);
                })
                .orElse(AiHealthReportResponse.noData(date, date));
    }

    AiHealthReportResponse generateRangeReport(String deviceId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating AI range report for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

        DailySummaryRangeSummaryResponse rangeSummary = dailySummaryFacade.getRangeSummary(deviceId, startDate, endDate);

        if (rangeSummary.daysWithData() == null || rangeSummary.daysWithData() == 0) {
            log.info("No data available for device {} range {} to {}", maskDeviceId(deviceId), startDate, endDate);
            return AiHealthReportResponse.noData(startDate, endDate);
        }

        String dataContext = buildRangeDataContext(rangeSummary, startDate, endDate);
        String report = callAi(RANGE_REPORT_SYSTEM_PROMPT,
                "Generate a detailed health report for the period " + startDate + " to " + endDate + " based on this data:\n\n" + dataContext);

        return AiHealthReportResponse.range(startDate, endDate, report, true);
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

    private String callAi(String systemPrompt, String userMessage) {
        String effectiveMessage = userMessage;
        if (effectiveMessage.length() > MAX_AI_INPUT_LENGTH) {
            log.warn("AI input truncated from {} to {} chars", effectiveMessage.length(), MAX_AI_INPUT_LENGTH);
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

            log.info("AI report generated: {} chars", content.length());
            return content;
        } catch (AiSummaryGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate AI report", e);
            throw new AiSummaryGenerationException("Failed to generate AI report", e);
        }
    }

    private String buildDailyDataContext(DailySummary summary) {
        StringBuilder sb = new StringBuilder();

        var activity = summary.activity();
        appendIfPositive(sb, "Steps", activity.steps());
        appendIfPositive(sb, "Active minutes", activity.activeMinutes());
        appendIfPositive(sb, "Active calories burned (kcal)", activity.activeCalories());
        if (activity.distanceMeters() != null && activity.distanceMeters() > 0) {
            sb.append("Distance: ").append(activity.distanceMeters()).append(" meters\n");
        }

        if (!summary.sleep().isEmpty()) {
            int totalSleepMinutes = summary.sleep().stream()
                    .mapToInt(s -> s.totalMinutes() != null ? s.totalMinutes() : 0)
                    .sum();
            sb.append("Sleep: ").append(totalSleepMinutes / 60).append("h ").append(totalSleepMinutes % 60).append("min\n");
        }

        if (!summary.workouts().isEmpty()) {
            sb.append("Workouts: ").append(summary.workouts().size()).append("\n");
            summary.workouts().stream()
                    .filter(w -> w.note() != null && !w.note().isBlank())
                    .map(w -> "  - " + sanitizeForPrompt(w.note()))
                    .forEach(line -> sb.append(line).append("\n"));
        }

        var nutrition = summary.nutrition();
        if (nutrition.mealCount() != null && nutrition.mealCount() > 0) {
            sb.append("Meals: ").append(nutrition.mealCount()).append("\n");
            appendIfPositive(sb, "  Calories (kcal)", nutrition.totalCalories());
            appendIfPositive(sb, "  Protein (g)", nutrition.totalProtein());
            appendIfPositive(sb, "  Fat (g)", nutrition.totalFat());
            appendIfPositive(sb, "  Carbs (g)", nutrition.totalCarbs());
        }

        if (!summary.meals().isEmpty()) {
            long healthyCount = summary.meals().stream()
                    .filter(m -> "VERY_HEALTHY".equals(m.healthRating()) || "HEALTHY".equals(m.healthRating()))
                    .count();
            long unhealthyCount = summary.meals().stream()
                    .filter(m -> "VERY_UNHEALTHY".equals(m.healthRating()) || "UNHEALTHY".equals(m.healthRating()))
                    .count();
            if (healthyCount > 0) sb.append("Healthy meals: ").append(healthyCount).append("\n");
            if (unhealthyCount > 0) sb.append("Unhealthy meals: ").append(unhealthyCount).append("\n");
        }

        var heart = summary.heart();
        appendIfPositive(sb, "Resting HR (bpm)", heart.restingBpm());
        appendIfPositive(sb, "Average HR (bpm)", heart.avgBpm());
        appendIfPositive(sb, "Max HR (bpm)", heart.maxBpm());

        return sb.toString();
    }

    private String buildRangeDataContext(DailySummaryRangeSummaryResponse range, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("Period: ").append(startDate).append(" to ").append(endDate).append("\n");
        sb.append("Days with data: ").append(range.daysWithData()).append("\n\n");

        var activity = range.activity();
        if (activity != null) {
            sb.append("--- ACTIVITY ---\n");
            sb.append("Total steps: ").append(activity.totalSteps()).append("\n");
            sb.append("Average steps/day: ").append(activity.averageSteps()).append("\n");
            sb.append("Total active minutes: ").append(activity.totalActiveMinutes()).append("\n");
            sb.append("Average active minutes/day: ").append(activity.averageActiveMinutes()).append("\n");
            sb.append("Total calories burned: ").append(activity.totalActiveCalories()).append(" kcal\n");
            sb.append("Average calories/day: ").append(activity.averageActiveCalories()).append(" kcal\n");
            if (activity.totalDistanceMeters() != null && activity.totalDistanceMeters() > 0) {
                sb.append("Total distance: ").append(activity.totalDistanceMeters()).append(" meters\n");
                sb.append("Average distance/day: ").append(activity.averageDistanceMeters()).append(" meters\n");
            }
            sb.append("\n");
        }

        var sleep = range.sleep();
        if (sleep != null && sleep.daysWithSleep() != null && sleep.daysWithSleep() > 0) {
            sb.append("--- SLEEP ---\n");
            sb.append("Average sleep: ").append(Optional.ofNullable(sleep.averageSleepMinutes()).map(m -> m / 60 + "h " + m % 60 + "min").orElse("N/A")).append("\n");
            sb.append("Days with sleep data: ").append(sleep.daysWithSleep()).append("\n\n");
        }

        var nutrition = range.nutrition();
        if (nutrition != null && nutrition.daysWithData() != null && nutrition.daysWithData() > 0) {
            sb.append("--- NUTRITION ---\n");
            sb.append("Average calories/day: ").append(nutrition.averageCalories()).append(" kcal\n");
            sb.append("Average protein/day: ").append(nutrition.averageProtein()).append("g\n");
            sb.append("Average fat/day: ").append(nutrition.averageFat()).append("g\n");
            sb.append("Average carbs/day: ").append(nutrition.averageCarbs()).append("g\n");
            sb.append("Average meals/day: ").append(nutrition.averageMealsPerDay()).append("\n");
            sb.append("Days with nutrition data: ").append(nutrition.daysWithData()).append("\n\n");
        }

        var workouts = range.workouts();
        if (workouts != null && workouts.totalWorkouts() != null && workouts.totalWorkouts() > 0) {
            sb.append("--- WORKOUTS ---\n");
            sb.append("Total workouts: ").append(workouts.totalWorkouts()).append("\n");
            sb.append("Days with workouts: ").append(workouts.daysWithWorkouts()).append("\n");
            if (workouts.workoutList() != null && !workouts.workoutList().isEmpty()) {
                workouts.workoutList().stream()
                        .map(w -> "  - " + w.date() + ": " + sanitizeForPrompt(Optional.ofNullable(w.note()).orElse("Workout")))
                        .forEach(line -> sb.append(line).append("\n"));
            }
            sb.append("\n");
        }

        var heart = range.heart();
        if (heart != null && heart.daysWithData() != null && heart.daysWithData() > 0) {
            sb.append("--- HEART RATE ---\n");
            if (heart.averageRestingBpm() != null) sb.append("Average resting HR: ").append(heart.averageRestingBpm()).append(" bpm\n");
            if (heart.averageDailyBpm() != null) sb.append("Average daily HR: ").append(heart.averageDailyBpm()).append(" bpm\n");
            if (heart.maxBpmOverall() != null) sb.append("Max HR: ").append(heart.maxBpmOverall()).append(" bpm\n");
            sb.append("Days with HR data: ").append(heart.daysWithData()).append("\n");
        }

        return sb.toString();
    }

    private void appendIfPositive(StringBuilder sb, String label, Integer value) {
        if (value != null && value > 0) {
            sb.append(label).append(": ").append(value).append("\n");
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
