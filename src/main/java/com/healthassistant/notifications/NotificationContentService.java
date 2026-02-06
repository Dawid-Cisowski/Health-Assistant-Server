package com.healthassistant.notifications;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
class NotificationContentService {

    private final DailySummaryFacade dailySummaryFacade;

    record NotificationContent(String title, String body, Map<String, String> data) {}

    Optional<NotificationContent> buildDailyNotification(String deviceId, LocalDate date) {
        return dailySummaryFacade.getDailySummary(deviceId, date)
                .map(summary -> {
                    String body = buildDailySummaryBody(summary);
                    Map<String, String> data = Map.of(
                            "type", "DAILY_REPORT",
                            "date", date.toString(),
                            "reportAvailable", "true"
                    );
                    return new NotificationContent("Podsumowanie dnia", body, data);
                });
    }

    Optional<NotificationContent> buildWeeklyNotification(String deviceId, LocalDate weekStart, LocalDate weekEnd) {
        DailySummaryRangeSummaryResponse range = dailySummaryFacade.getRangeSummary(deviceId, weekStart, weekEnd);
        if (range.daysWithData() == null || range.daysWithData() == 0) {
            return Optional.empty();
        }

        String body = buildRangeSummaryBody(range, "tygodnia");
        Map<String, String> data = Map.of(
                "type", "WEEKLY_REPORT",
                "startDate", weekStart.toString(),
                "endDate", weekEnd.toString(),
                "reportAvailable", "true"
        );
        return Optional.of(new NotificationContent("Podsumowanie tygodnia", body, data));
    }

    Optional<NotificationContent> buildMonthlyNotification(String deviceId, LocalDate monthStart, LocalDate monthEnd) {
        DailySummaryRangeSummaryResponse range = dailySummaryFacade.getRangeSummary(deviceId, monthStart, monthEnd);
        if (range.daysWithData() == null || range.daysWithData() == 0) {
            return Optional.empty();
        }

        String body = buildRangeSummaryBody(range, "miesiaca");
        Map<String, String> data = Map.of(
                "type", "MONTHLY_REPORT",
                "startDate", monthStart.toString(),
                "endDate", monthEnd.toString(),
                "reportAvailable", "true"
        );
        return Optional.of(new NotificationContent("Podsumowanie miesiaca", body, data));
    }

    private String buildDailySummaryBody(DailySummary summary) {
        StringBuilder sb = new StringBuilder();
        Integer steps = summary.getTotalSteps();
        if (steps != null && steps > 0) {
            sb.append(formatNumber(steps)).append(" krokow");
        }

        Integer sleepMinutes = summary.getTotalSleepMinutes();
        if (sleepMinutes != null && sleepMinutes > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(sleepMinutes / 60).append("h").append(sleepMinutes % 60).append("min snu");
        }

        int workoutCount = summary.getWorkoutCount();
        if (workoutCount > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(workoutCount).append(workoutCount == 1 ? " trening" : " treningi");
        }

        return sb.isEmpty() ? "Sprawdz swoje dane zdrowotne" : sb.toString();
    }

    private String buildRangeSummaryBody(DailySummaryRangeSummaryResponse range, String periodName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Podsumowanie ").append(periodName).append(": ");

        var activity = range.activity();
        if (activity != null && activity.averageSteps() != null && activity.averageSteps() > 0) {
            sb.append("sr. ").append(formatNumber(activity.averageSteps())).append(" krokow/dzien");
        }

        var workouts = range.workouts();
        if (workouts != null && workouts.totalWorkouts() != null && workouts.totalWorkouts() > 0) {
            if (sb.length() > ("Podsumowanie " + periodName + ": ").length()) sb.append(", ");
            sb.append(workouts.totalWorkouts()).append(" treningow");
        }

        return sb.toString();
    }

    private String formatNumber(int number) {
        if (number >= 1000) {
            return String.valueOf(number);
        }
        return String.valueOf(number);
    }
}
