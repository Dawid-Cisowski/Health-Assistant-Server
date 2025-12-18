package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.AiDailySummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AiDailySummaryService {

    private final DailySummaryFacade dailySummaryFacade;
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
        You are a health assistant that writes very short daily summaries.

        RULES:
        - Write in Polish, informal, friendly tone
        - MAXIMUM 3 sentences
        - Be direct and have personality
        - Pick only the most important things to comment on
        - You can use emojis (sparingly)
        - DO NOT use words: "podsumowanie", "dzisiaj", "twoje dane"
        - DO NOT start with "Hej" or "Czesc"
        - If the day was good - praise, if bad - comment with humor or empathy

        EXAMPLE STYLES:
        - "super dzien! wyspales sie, zdrowe jedzenie i duzo krokow - szacun"
        - "no slabo to wyglada... malo snu i sniadanie moglo byc lepsze"
        - "solidnie sie narobiles na silce, tylko ten sen moglby byc dluzszy"
        """;

    public AiDailySummaryResponse generateSummary(LocalDate date) {
        log.info("Generating AI daily summary for date: {}", date);

        return dailySummaryFacade.getDailySummary(date)
                .map(this::generateFromData)
                .map(text -> new AiDailySummaryResponse(date, text, true))
                .orElse(AiDailySummaryResponse.noData(date));
    }

    private String generateFromData(DailySummary summary) {
        String dataContext = buildDataContext(summary);

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

    private String buildDataContext(DailySummary summary) {
        StringBuilder sb = new StringBuilder();

        var activity = summary.activity();
        if (activity.steps() != null && activity.steps() > 0) {
            sb.append("Steps: ").append(activity.steps()).append("\n");
        }
        if (activity.activeMinutes() != null && activity.activeMinutes() > 0) {
            sb.append("Active minutes: ").append(activity.activeMinutes()).append("\n");
        }
        if (activity.activeCalories() != null && activity.activeCalories() > 0) {
            sb.append("Calories burned: ").append(activity.activeCalories()).append(" kcal\n");
        }

        if (!summary.sleep().isEmpty()) {
            int totalMinutes = summary.sleep().stream()
                    .mapToInt(s -> s.totalMinutes() != null ? s.totalMinutes() : 0)
                    .sum();
            int hours = totalMinutes / 60;
            int mins = totalMinutes % 60;
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
        if (nutrition.mealCount() != null && nutrition.mealCount() > 0) {
            sb.append("Meals: ").append(nutrition.mealCount()).append("\n");
            if (nutrition.totalCalories() != null) {
                sb.append("  Calories: ").append(nutrition.totalCalories()).append(" kcal\n");
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

        return sb.toString();
    }
}
