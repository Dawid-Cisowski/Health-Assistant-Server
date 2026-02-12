package com.healthassistant.reports;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.reports.api.dto.GoalEvaluation;
import com.healthassistant.reports.api.dto.GoalResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
class GoalsEvaluator {

    private static final int DAILY_STEPS_TARGET = 10_000;
    private static final int DAILY_SLEEP_MINUTES_TARGET = 420;
    private static final int DAILY_ACTIVE_MINUTES_TARGET = 240;
    private static final int DAILY_BURNED_CALORIES_TARGET = 600;
    private static final int DAILY_HEALTHY_MEALS_TARGET = 3;
    private static final int RESTING_HR_MIN = 50;
    private static final int RESTING_HR_MAX = 80;
    private static final double CALORIE_TOLERANCE = 0.20;

    private static final int WEEKLY_WORKOUTS_TARGET = 3;
    private static final int WEEKLY_ACTIVE_MINUTES_TARGET = 150;
    private static final int WEEKLY_NUTRITION_DAYS_TARGET = 5;

    private static final int MONTHLY_WORKOUTS_TARGET = 18;
    private static final int MONTHLY_ACTIVE_MINUTES_TARGET = 600;
    private static final int MONTHLY_NUTRITION_DAYS_TARGET = 20;
    private static final int MONTHLY_PRS_TARGET = 3;

    GoalEvaluation evaluateDaily(DailySummary summary, Optional<EnergyRequirementsResponse> energyReq) {
        List<GoalResult> goals = new ArrayList<>();

        int steps = orZero(summary.getTotalSteps());
        goals.add(new GoalResult("steps", "Kroki", steps >= DAILY_STEPS_TARGET,
                formatNumber(steps), ">= " + formatNumber(DAILY_STEPS_TARGET)));

        int sleepMinutes = orZero(summary.getTotalSleepMinutes());
        goals.add(new GoalResult("sleep", "Sen", sleepMinutes >= DAILY_SLEEP_MINUTES_TARGET,
                formatDuration(sleepMinutes), ">= 7h"));

        int activeMinutes = orZero(summary.activity().activeMinutes());
        goals.add(new GoalResult("activeMinutes", "Aktywnosc", activeMinutes >= DAILY_ACTIVE_MINUTES_TARGET,
                formatDuration(activeMinutes), ">= 4h"));

        int burnedCalories = orZero(summary.getActiveCalories());
        goals.add(new GoalResult("burnedCalories", "Spalone kalorie", burnedCalories >= DAILY_BURNED_CALORIES_TARGET,
                burnedCalories + " kcal", ">= " + DAILY_BURNED_CALORIES_TARGET + " kcal"));

        energyReq.ifPresent(req -> {
            int targetCal = req.targetCaloriesKcal();
            int consumed = req.consumed() != null ? req.consumed().caloriesKcal() : 0;
            int lowerBound = (int) (targetCal * (1 - CALORIE_TOLERANCE));
            int upperBound = (int) (targetCal * (1 + CALORIE_TOLERANCE));
            boolean achieved = consumed >= lowerBound && consumed <= upperBound;
            goals.add(new GoalResult("calorieIntake", "Kalorie", achieved,
                    formatNumber(consumed) + " kcal",
                    formatNumber(lowerBound) + "-" + formatNumber(upperBound) + " kcal"));

            int targetProtein = req.macroTargets() != null && req.macroTargets().proteinGrams() != null
                    ? req.macroTargets().proteinGrams() : 0;
            int consumedProtein = req.consumed() != null ? req.consumed().proteinGrams() : 0;
            goals.add(new GoalResult("protein", "Bialko", consumedProtein >= targetProtein,
                    consumedProtein + "g", ">= " + targetProtein + "g"));
        });

        long healthyMealCount = summary.meals().stream()
                .filter(m -> "VERY_HEALTHY".equals(m.healthRating()) || "HEALTHY".equals(m.healthRating()))
                .count();
        goals.add(new GoalResult("healthyMeals", "Zdrowe posilki",
                healthyMealCount >= DAILY_HEALTHY_MEALS_TARGET,
                String.valueOf(healthyMealCount), ">= " + DAILY_HEALTHY_MEALS_TARGET));

        Integer restingBpm = summary.heart().restingBpm();
        if (restingBpm != null && restingBpm > 0) {
            boolean hrAchieved = restingBpm >= RESTING_HR_MIN && restingBpm <= RESTING_HR_MAX;
            goals.add(new GoalResult("restingHr", "Tetno spoczynkowe", hrAchieved,
                    restingBpm + " bpm", RESTING_HR_MIN + "-" + RESTING_HR_MAX + " bpm"));
        }

        int achieved = (int) goals.stream().filter(GoalResult::achieved).count();
        return new GoalEvaluation(goals, achieved, goals.size());
    }

    GoalEvaluation evaluateWeekly(DailySummaryRangeSummaryResponse range,
                                  List<DayNutritionCheck> dailyChecks) {
        List<GoalResult> goals = new ArrayList<>();

        int avgSteps = orZero(range.activity() != null ? range.activity().averageSteps() : null);
        goals.add(new GoalResult("avgSteps", "Sr. kroki/dzien", avgSteps >= DAILY_STEPS_TARGET,
                formatNumber(avgSteps), ">= " + formatNumber(DAILY_STEPS_TARGET)));

        int avgSleep = orZero(range.sleep() != null ? range.sleep().averageSleepMinutes() : null);
        goals.add(new GoalResult("avgSleep", "Sr. sen/noc", avgSleep >= DAILY_SLEEP_MINUTES_TARGET,
                formatDuration(avgSleep), ">= 7h"));

        int totalWorkouts = orZero(range.workouts() != null ? range.workouts().totalWorkouts() : null);
        goals.add(new GoalResult("workouts", "Treningi", totalWorkouts >= WEEKLY_WORKOUTS_TARGET,
                String.valueOf(totalWorkouts), ">= " + WEEKLY_WORKOUTS_TARGET));

        int totalActiveMinutes = orZero(range.activity() != null ? range.activity().totalActiveMinutes() : null);
        goals.add(new GoalResult("activeMinutes", "Aktywne minuty", totalActiveMinutes >= WEEKLY_ACTIVE_MINUTES_TARGET,
                totalActiveMinutes + " min", ">= " + WEEKLY_ACTIVE_MINUTES_TARGET + " min (WHO)"));

        long calorieOkDays = dailyChecks.stream().filter(DayNutritionCheck::caloriesOnTarget).count();
        goals.add(new GoalResult("calorieConsistency", "Kalorie (dni w normie)",
                calorieOkDays >= WEEKLY_NUTRITION_DAYS_TARGET,
                calorieOkDays + "/7 dni", ">= " + WEEKLY_NUTRITION_DAYS_TARGET + "/7 dni"));

        long proteinOkDays = dailyChecks.stream().filter(DayNutritionCheck::proteinOnTarget).count();
        goals.add(new GoalResult("proteinConsistency", "Bialko (dni w normie)",
                proteinOkDays >= WEEKLY_NUTRITION_DAYS_TARGET,
                proteinOkDays + "/7 dni", ">= " + WEEKLY_NUTRITION_DAYS_TARGET + "/7 dni"));

        int achieved = (int) goals.stream().filter(GoalResult::achieved).count();
        return new GoalEvaluation(goals, achieved, goals.size());
    }

    GoalEvaluation evaluateMonthly(DailySummaryRangeSummaryResponse range,
                                   List<DayNutritionCheck> dailyChecks,
                                   int newPrsCount) {
        List<GoalResult> goals = new ArrayList<>();

        int avgSteps = orZero(range.activity() != null ? range.activity().averageSteps() : null);
        goals.add(new GoalResult("avgSteps", "Sr. kroki/dzien", avgSteps >= DAILY_STEPS_TARGET,
                formatNumber(avgSteps), ">= " + formatNumber(DAILY_STEPS_TARGET)));

        int avgSleep = orZero(range.sleep() != null ? range.sleep().averageSleepMinutes() : null);
        goals.add(new GoalResult("avgSleep", "Sr. sen/noc", avgSleep >= DAILY_SLEEP_MINUTES_TARGET,
                formatDuration(avgSleep), ">= 7h"));

        int totalWorkouts = orZero(range.workouts() != null ? range.workouts().totalWorkouts() : null);
        goals.add(new GoalResult("workouts", "Treningi", totalWorkouts >= MONTHLY_WORKOUTS_TARGET,
                String.valueOf(totalWorkouts), ">= " + MONTHLY_WORKOUTS_TARGET));

        int totalActiveMinutes = orZero(range.activity() != null ? range.activity().totalActiveMinutes() : null);
        goals.add(new GoalResult("activeMinutes", "Aktywne minuty", totalActiveMinutes >= MONTHLY_ACTIVE_MINUTES_TARGET,
                totalActiveMinutes + " min", ">= " + MONTHLY_ACTIVE_MINUTES_TARGET + " min"));

        long calorieOkDays = dailyChecks.stream().filter(DayNutritionCheck::caloriesOnTarget).count();
        int totalDays = dailyChecks.size();
        goals.add(new GoalResult("calorieConsistency", "Kalorie (dni w normie)",
                calorieOkDays >= MONTHLY_NUTRITION_DAYS_TARGET,
                calorieOkDays + "/" + totalDays + " dni", ">= " + MONTHLY_NUTRITION_DAYS_TARGET + " dni"));

        long proteinOkDays = dailyChecks.stream().filter(DayNutritionCheck::proteinOnTarget).count();
        goals.add(new GoalResult("proteinConsistency", "Bialko (dni w normie)",
                proteinOkDays >= MONTHLY_NUTRITION_DAYS_TARGET,
                proteinOkDays + "/" + totalDays + " dni", ">= " + MONTHLY_NUTRITION_DAYS_TARGET + " dni"));

        goals.add(new GoalResult("personalRecords", "Nowe rekordy (PR)", newPrsCount >= MONTHLY_PRS_TARGET,
                String.valueOf(newPrsCount), ">= " + MONTHLY_PRS_TARGET));

        int achieved = (int) goals.stream().filter(GoalResult::achieved).count();
        return new GoalEvaluation(goals, achieved, goals.size());
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    private static String formatNumber(int number) {
        return String.format("%,d", number);
    }

    private static String formatDuration(int minutes) {
        return minutes / 60 + "h " + minutes % 60 + "min";
    }

    record DayNutritionCheck(LocalDate date, int targetCalories, int consumedCalories,
                             int targetProtein, int consumedProtein) {
        boolean caloriesOnTarget() {
            if (targetCalories <= 0) return false;
            int lower = (int) (targetCalories * (1 - CALORIE_TOLERANCE));
            int upper = (int) (targetCalories * (1 + CALORIE_TOLERANCE));
            return consumedCalories >= lower && consumedCalories <= upper;
        }

        boolean proteinOnTarget() {
            return consumedProtein >= targetProtein;
        }
    }
}
