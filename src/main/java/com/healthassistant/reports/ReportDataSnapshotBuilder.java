package com.healthassistant.reports;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.reports.api.dto.RangeReportDataSnapshot;
import com.healthassistant.reports.api.dto.ReportDataSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class ReportDataSnapshotBuilder {

    ReportDataSnapshot buildDaily(DailySummary summary, Optional<EnergyRequirementsResponse> energyReq) {
        var activity = new ReportDataSnapshot.ActivityData(
                summary.activity().steps(),
                summary.activity().activeMinutes(),
                summary.activity().activeCalories(),
                summary.activity().distanceMeters()
        );

        var sleepSessions = summary.sleep().stream()
                .map(s -> new ReportDataSnapshot.SleepSession(s.start(), s.end(), s.totalMinutes()))
                .toList();
        var sleep = new ReportDataSnapshot.SleepData(summary.getTotalSleepMinutes(), sleepSessions);

        long healthyMealCount = summary.meals().stream()
                .filter(m -> "VERY_HEALTHY".equals(m.healthRating()) || "HEALTHY".equals(m.healthRating()))
                .count();

        ReportDataSnapshot.NutritionTarget target = energyReq
                .map(req -> new ReportDataSnapshot.NutritionTarget(
                        req.targetCaloriesKcal(),
                        req.macroTargets() != null && req.macroTargets().proteinGrams() != null
                                ? req.macroTargets().proteinGrams() : 0,
                        req.macroTargets() != null && req.macroTargets().fatGrams() != null
                                ? req.macroTargets().fatGrams() : 0,
                        req.macroTargets() != null && req.macroTargets().carbsGrams() != null
                                ? req.macroTargets().carbsGrams() : 0
                ))
                .orElse(null);

        var nutrition = new ReportDataSnapshot.NutritionData(
                summary.nutrition().totalCalories(),
                summary.nutrition().totalProtein(),
                summary.nutrition().totalFat(),
                summary.nutrition().totalCarbs(),
                summary.nutrition().mealCount(),
                (int) healthyMealCount,
                target
        );

        var workouts = summary.workouts().stream()
                .map(w -> new ReportDataSnapshot.WorkoutData(w.workoutId(), w.note(), w.performedAt()))
                .toList();

        var heartRate = new ReportDataSnapshot.HeartRateData(
                summary.heart().restingBpm(),
                summary.heart().avgBpm(),
                summary.heart().maxBpm()
        );

        return new ReportDataSnapshot(activity, sleep, nutrition, workouts, heartRate);
    }

    RangeReportDataSnapshot buildRange(DailySummaryRangeSummaryResponse range) {
        var actSummary = range.activity();
        var activity = actSummary != null
                ? new RangeReportDataSnapshot.RangeActivityData(
                        actSummary.totalSteps(), actSummary.averageSteps(),
                        actSummary.totalActiveMinutes(), actSummary.averageActiveMinutes(),
                        actSummary.totalActiveCalories(), actSummary.averageActiveCalories(),
                        actSummary.totalDistanceMeters())
                : null;

        var slpSummary = range.sleep();
        var sleep = slpSummary != null
                ? new RangeReportDataSnapshot.RangeSleepData(
                        slpSummary.totalSleepMinutes(), slpSummary.averageSleepMinutes(),
                        slpSummary.daysWithSleep())
                : null;

        var nutSummary = range.nutrition();
        var nutrition = nutSummary != null
                ? new RangeReportDataSnapshot.RangeNutritionData(
                        nutSummary.totalCalories(), nutSummary.averageCalories(),
                        nutSummary.totalProtein(), nutSummary.averageProtein(),
                        nutSummary.totalFat(), nutSummary.averageFat(),
                        nutSummary.totalCarbs(), nutSummary.averageCarbs(),
                        nutSummary.totalMeals(), nutSummary.daysWithData())
                : null;

        var wrkSummary = range.workouts();
        var workouts = wrkSummary != null
                ? new RangeReportDataSnapshot.RangeWorkoutData(
                        wrkSummary.totalWorkouts(), wrkSummary.daysWithWorkouts(),
                        wrkSummary.workoutList() != null
                                ? wrkSummary.workoutList().stream()
                                        .map(w -> new RangeReportDataSnapshot.RangeWorkoutEntry(
                                                w.workoutId(), w.note(), w.date()))
                                        .toList()
                                : List.of())
                : null;

        var hrtSummary = range.heart();
        var heartRate = hrtSummary != null
                ? new RangeReportDataSnapshot.RangeHeartRateData(
                        hrtSummary.averageRestingBpm(), hrtSummary.averageDailyBpm(),
                        hrtSummary.maxBpmOverall(), hrtSummary.daysWithData())
                : null;

        List<RangeReportDataSnapshot.DailyBreakdownEntry> breakdown = range.dailyStats() != null
                ? range.dailyStats().stream()
                        .map(this::toDailyBreakdown)
                        .toList()
                : List.of();

        return new RangeReportDataSnapshot(
                range.daysWithData(), activity, sleep, nutrition, workouts, heartRate, breakdown);
    }

    private RangeReportDataSnapshot.DailyBreakdownEntry toDailyBreakdown(DailySummaryResponse day) {
        Integer totalSleepMinutes = day.sleep() != null
                ? day.sleep().stream()
                        .mapToInt(s -> s.totalMinutes() != null ? s.totalMinutes() : 0)
                        .sum()
                : null;
        return new RangeReportDataSnapshot.DailyBreakdownEntry(
                day.date(),
                day.activity() != null ? day.activity().steps() : null,
                totalSleepMinutes,
                day.activity() != null ? day.activity().activeMinutes() : null,
                day.activity() != null ? day.activity().activeCalories() : null,
                day.nutrition() != null ? day.nutrition().totalCalories() : null,
                day.nutrition() != null ? day.nutrition().totalProtein() : null,
                day.workouts() != null ? day.workouts().size() : 0
        );
    }
}
