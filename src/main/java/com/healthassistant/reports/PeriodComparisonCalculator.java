package com.healthassistant.reports;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.reports.api.dto.ComparisonMetric;
import com.healthassistant.reports.api.dto.PeriodComparison;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
class PeriodComparisonCalculator {

    PeriodComparison compareDaily(DailySummary current, DailySummary previous, LocalDate prevDate) {
        List<ComparisonMetric> metrics = new ArrayList<>();

        metrics.add(buildMetric("steps", "Kroki",
                orZero(current.getTotalSteps()), orZero(previous.getTotalSteps())));
        metrics.add(buildMetric("sleep", "Sen (min)",
                orZero(current.getTotalSleepMinutes()), orZero(previous.getTotalSleepMinutes())));
        metrics.add(buildMetric("activeMinutes", "Aktywne minuty",
                orZero(current.activity().activeMinutes()), orZero(previous.activity().activeMinutes())));
        metrics.add(buildMetric("burnedCalories", "Spalone kalorie",
                orZero(current.getActiveCalories()), orZero(previous.getActiveCalories())));
        metrics.add(buildMetric("calorieIntake", "Kalorie (intake)",
                orZero(current.nutrition().totalCalories()), orZero(previous.nutrition().totalCalories())));
        metrics.add(buildMetric("protein", "Bialko",
                orZero(current.nutrition().totalProtein()), orZero(previous.nutrition().totalProtein())));
        metrics.add(buildMetric("workouts", "Treningi",
                current.getWorkoutCount(), previous.getWorkoutCount()));

        return new PeriodComparison(prevDate, prevDate, metrics);
    }

    PeriodComparison compareRange(DailySummaryRangeSummaryResponse current,
                                  DailySummaryRangeSummaryResponse previous,
                                  LocalDate prevStart, LocalDate prevEnd) {
        List<ComparisonMetric> metrics = new ArrayList<>();

        metrics.add(buildMetric("avgSteps", "Sr. kroki/dzien",
                safeActivity(current, a -> a.averageSteps()),
                safeActivity(previous, a -> a.averageSteps())));
        metrics.add(buildMetric("avgSleep", "Sr. sen (min)",
                safeSleep(current, s -> s.averageSleepMinutes()),
                safeSleep(previous, s -> s.averageSleepMinutes())));
        metrics.add(buildMetric("totalActiveMinutes", "Aktywne minuty",
                safeActivity(current, a -> a.totalActiveMinutes()),
                safeActivity(previous, a -> a.totalActiveMinutes())));
        metrics.add(buildMetric("totalBurnedCalories", "Spalone kalorie",
                safeActivity(current, a -> a.totalActiveCalories()),
                safeActivity(previous, a -> a.totalActiveCalories())));
        metrics.add(buildMetric("totalCalorieIntake", "Kalorie (intake)",
                safeNutrition(current, n -> n.totalCalories()),
                safeNutrition(previous, n -> n.totalCalories())));
        metrics.add(buildMetric("totalProtein", "Bialko",
                safeNutrition(current, n -> n.totalProtein()),
                safeNutrition(previous, n -> n.totalProtein())));
        metrics.add(buildMetric("workouts", "Treningi",
                safeWorkouts(current), safeWorkouts(previous)));

        return new PeriodComparison(prevStart, prevEnd, metrics);
    }

    private static ComparisonMetric buildMetric(String name, String displayName,
                                                int currentValue, int previousValue) {
        Double changePercent = previousValue != 0
                ? Math.round((currentValue - previousValue) * 1000.0 / previousValue) / 10.0
                : null;
        return new ComparisonMetric(name, displayName, currentValue, previousValue, changePercent);
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static int safeActivity(DailySummaryRangeSummaryResponse r,
                                    java.util.function.Function<DailySummaryRangeSummaryResponse.ActivitySummary, Integer> extractor) {
        return r.activity() != null ? orZero(extractor.apply(r.activity())) : 0;
    }

    private static int safeSleep(DailySummaryRangeSummaryResponse r,
                                 java.util.function.Function<DailySummaryRangeSummaryResponse.SleepSummary, Integer> extractor) {
        return r.sleep() != null ? orZero(extractor.apply(r.sleep())) : 0;
    }

    private static int safeNutrition(DailySummaryRangeSummaryResponse r,
                                     java.util.function.Function<DailySummaryRangeSummaryResponse.NutritionSummary, Integer> extractor) {
        return r.nutrition() != null ? orZero(extractor.apply(r.nutrition())) : 0;
    }

    private static int safeWorkouts(DailySummaryRangeSummaryResponse r) {
        return r.workouts() != null ? orZero(r.workouts().totalWorkouts()) : 0;
    }
}
