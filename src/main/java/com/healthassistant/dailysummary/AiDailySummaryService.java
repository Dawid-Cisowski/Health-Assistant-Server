package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.AiDailySummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AiDailySummaryService {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private static final int GOAL_STEPS = 10_000;
    private static final int GOAL_SLEEP_MINUTES = 420;
    private static final int GOAL_CALORIES = 2500;
    private static final int GOAL_ACTIVITY_MINUTES = 300;

    private final DailySummaryFacade dailySummaryFacade;
    private final DailySummaryJpaRepository repository;
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
        You are a health assistant that writes very short daily summaries.

        USER PROFILE:
        - Male, 21 years old
        - Height: 178 cm, Weight: 73 kg
        - Goals: Maintain healthy lifestyle and build muscle mass

        RULES:
        - Write in Polish, informal, friendly tone
        - MAXIMUM 3 sentences
        - Be direct and have personality
        - Pick only the most important things to comment on
        - You can use emojis (sparingly)
        - DO NOT use words: "podsumowanie", "dzisiaj", "twoje dane"
        - DO NOT start with "Hej" or "Czesc"
        - Consider user's muscle building goal when commenting on workouts and nutrition

        GOAL EVALUATION (IMPORTANT):
        - User goals: Steps=10000, Sleep=7h, Calories=2500kcal, Activity=5h
        - TOLERANCE: ±10% from goal is "achieved" (e.g., 6h50m sleep vs 7h goal = OK, don't criticize!)
        - Sleep is ALWAYS evaluated independently of time of day (previous night)

        TIME-AWARE EVALUATION (for steps, calories, activity - NOT sleep):
        - MORNING (before noon): 20-40% of goal is good progress
        - AFTERNOON: 40-70% of goal expected
        - EVENING: 70-100% expected
        - NIGHT: full day evaluation

        EXAMPLES:
        - Morning + 2000 steps + 1 meal → "niezly start! dzien dopiero sie rozkreca"
        - Evening + 3000 steps + 1 meal → "slabo... malo krokow jak na te pore, daj z siebie wiecej"
        - Sleep 6h50m (goal 7h) → "sen ok" (within tolerance, don't criticize)
        - Sleep 5h → "za malo snu, jutro sie odśpisz?"

        STYLE EXAMPLES:
        - "super dzien! wyspales sie, zdrowe jedzenie i duzo krokow - szacun"
        - "no slabo to wyglada... malo snu i sniadanie moglo byc lepsze"
        - "solidnie sie narobiles na silce, tylko ten sen moglby byc dluzszy"
        """;

    @Transactional
    public AiDailySummaryResponse generateSummary(String deviceId, LocalDate date) {
        log.info("Generating AI daily summary for device {} date: {}", deviceId, date);

        var entityOpt = repository.findByDeviceIdAndDate(deviceId, date);
        if (entityOpt.isEmpty()) {
            log.info("No daily summary entity found for device {} date: {}", deviceId, date);
            return AiDailySummaryResponse.noData(date);
        }

        DailySummaryJpaEntity entity = entityOpt.get();

        if (isCacheValid(entity)) {
            log.info("Returning cached AI summary for device {} date: {} (generated at: {})",
                    deviceId, date, entity.getAiSummaryGeneratedAt());
            return new AiDailySummaryResponse(date, entity.getAiSummary(), true);
        }

        String timeOfDay = getTimeOfDayContext();
        return dailySummaryFacade.getDailySummary(deviceId, date)
                .map(summary -> {
                    String aiText = generateFromData(summary, timeOfDay);
                    cacheAiSummary(entity, aiText);
                    return new AiDailySummaryResponse(date, aiText, true);
                })
                .orElse(AiDailySummaryResponse.noData(date));
    }

    private boolean isCacheValid(DailySummaryJpaEntity entity) {
        if (entity.getAiSummary() == null || entity.getAiSummaryGeneratedAt() == null) {
            return false;
        }
        if (entity.getLastEventAt() == null) {
            return true;
        }
        return !entity.getLastEventAt().isAfter(entity.getAiSummaryGeneratedAt());
    }

    private void cacheAiSummary(DailySummaryJpaEntity entity, String aiSummary) {
        entity.setAiSummary(aiSummary);
        entity.setAiSummaryGeneratedAt(Instant.now());
        repository.save(entity);
        log.info("Cached AI summary for date: {}", entity.getDate());
    }

    private String getTimeOfDayContext() {
        LocalTime now = LocalTime.now(POLAND_ZONE);
        int hour = now.getHour();

        if (hour < 12) return "MORNING (before noon)";
        if (hour < 17) return "AFTERNOON";
        if (hour < 21) return "EVENING";
        return "NIGHT";
    }

    private String generateFromData(DailySummary summary, String timeOfDay) {
        String dataContext = buildDataContext(summary, timeOfDay);

        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Write a short summary of this day based on the data:\n\n" + dataContext)
                    .call()
                    .content();

            log.info("AI summary generated: {} chars", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("Failed to generate AI summary", e);
            throw new AiSummaryGenerationException("Failed to generate AI summary", e);
        }
    }

    private String buildDataContext(DailySummary summary, String timeOfDay) {
        StringBuilder sb = new StringBuilder();

        var activity = summary.activity();
        int steps = activity.steps() != null ? activity.steps() : 0;
        int activeMinutes = activity.activeMinutes() != null ? activity.activeMinutes() : 0;
        int activeCalories = activity.activeCalories() != null ? activity.activeCalories() : 0;

        if (steps > 0) {
            sb.append("Steps: ").append(steps).append("\n");
        }
        if (activeMinutes > 0) {
            sb.append("Active minutes: ").append(activeMinutes).append("\n");
        }
        if (activeCalories > 0) {
            sb.append("Calories burned: ").append(activeCalories).append(" kcal\n");
        }

        int totalSleepMinutes = 0;
        if (!summary.sleep().isEmpty()) {
            totalSleepMinutes = summary.sleep().stream()
                    .mapToInt(s -> s.totalMinutes() != null ? s.totalMinutes() : 0)
                    .sum();
            int hours = totalSleepMinutes / 60;
            int mins = totalSleepMinutes % 60;
            sb.append("Sleep: ").append(hours).append("h ").append(mins).append("min\n");
        }

        if (!summary.workouts().isEmpty()) {
            sb.append("Workouts: ").append(summary.workouts().size()).append("\n");
            summary.workouts().forEach(w -> {
                if (w.note() != null && !w.note().isBlank()) {
                    sb.append("  - ").append(w.note()).append("\n");
                }
            });
        }

        var nutrition = summary.nutrition();
        int mealCalories = nutrition.totalCalories() != null ? nutrition.totalCalories() : 0;
        if (nutrition.mealCount() != null && nutrition.mealCount() > 0) {
            sb.append("Meals: ").append(nutrition.mealCount()).append("\n");
            if (mealCalories > 0) {
                sb.append("  Calories: ").append(mealCalories).append(" kcal\n");
            }
            if (nutrition.totalProtein() != null) {
                sb.append("  Protein: ").append(nutrition.totalProtein()).append("g\n");
            }
        }

        if (!summary.meals().isEmpty()) {
            long healthyCount = summary.meals().stream()
                    .filter(m -> "VERY_HEALTHY".equals(m.healthRating()) || "HEALTHY".equals(m.healthRating()))
                    .count();
            long unhealthyCount = summary.meals().stream()
                    .filter(m -> "VERY_UNHEALTHY".equals(m.healthRating()) || "UNHEALTHY".equals(m.healthRating()))
                    .count();
            if (healthyCount > 0) {
                sb.append("Healthy meals: ").append(healthyCount).append("\n");
            }
            if (unhealthyCount > 0) {
                sb.append("Unhealthy meals: ").append(unhealthyCount).append("\n");
            }
        }

        sb.append("\n--- CONTEXT ---\n");
        sb.append("Time of day: ").append(timeOfDay).append("\n");
        sb.append("Goals: Steps=10000, Sleep=7h, Calories=2500kcal, Activity=5h\n");

        if (steps > 0) {
            int stepsPct = (steps * 100) / GOAL_STEPS;
            sb.append("Steps progress: ").append(stepsPct).append("% of goal\n");
        }
        if (totalSleepMinutes > 0) {
            int sleepPct = (totalSleepMinutes * 100) / GOAL_SLEEP_MINUTES;
            sb.append("Sleep progress: ").append(sleepPct).append("% of goal\n");
        }
        if (mealCalories > 0) {
            int caloriesPct = (mealCalories * 100) / GOAL_CALORIES;
            sb.append("Calories progress: ").append(caloriesPct).append("% of goal\n");
        }
        if (activeMinutes > 0) {
            int activityPct = (activeMinutes * 100) / GOAL_ACTIVITY_MINUTES;
            sb.append("Activity progress: ").append(activityPct).append("% of goal\n");
        }

        return sb.toString();
    }
}
