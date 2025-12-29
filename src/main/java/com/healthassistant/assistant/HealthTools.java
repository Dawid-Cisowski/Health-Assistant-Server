package com.healthassistant.assistant;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class HealthTools {

    private final StepsFacade stepsFacade;
    private final SleepFacade sleepFacade;
    private final WorkoutFacade workoutFacade;
    private final DailySummaryFacade dailySummaryFacade;
    private final MealsFacade mealsFacade;

    @Tool(name = "getStepsData",
          description = "Retrieves user's step data for the given date range. Returns step count, distance, active hours and minutes. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public StepsRangeSummaryResponse getStepsData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching steps data for device {} from {} to {}", deviceId, startDate, endDate);

        var start = LocalDate.parse(startDate);
        var end = LocalDate.parse(endDate);
        var result = stepsFacade.getRangeSummary(deviceId, start, end);

        log.info("Steps data fetched: {} total steps over {} days", result.totalSteps(), result.daysWithData());
        return result;
    }

    @Tool(name = "getSleepData",
          description = "Retrieves user's sleep data for the given date range. Returns information about sleep duration, quality and sleep sessions. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public SleepRangeSummaryResponse getSleepData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching sleep data for device {} from {} to {}", deviceId, startDate, endDate);

        var start = LocalDate.parse(startDate);
        var end = LocalDate.parse(endDate);
        var result = sleepFacade.getRangeSummary(start, end);

        log.info("Sleep data fetched: {} total minutes over {} days", result.totalSleepMinutes(), result.daysWithData());
        return result;
    }

    @Tool(name = "getWorkoutData",
          description = "Retrieves user's strength training data for the given date range. Returns a list of workouts with exercises, sets and volume. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public List<WorkoutDetailResponse> getWorkoutData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching workout data for device {} from {} to {}", deviceId, startDate, endDate);

        var start = LocalDate.parse(startDate);
        var end = LocalDate.parse(endDate);
        var result = workoutFacade.getWorkoutsByDateRange(start, end);

        log.info("Workout data fetched: {} workouts", result.size());
        return result;
    }

    @Tool(name = "getDailySummary",
          description = "Retrieves complete daily summary for the user. Contains all data: activity (steps, calories), sleep, heart rate, meals and workouts. " +
                        "PARAMETER: date must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Optional<DailySummary> getDailySummary(String date) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching daily summary for device {} for date {}", deviceId, date);

        var localDate = LocalDate.parse(date);
        var result = dailySummaryFacade.getDailySummary(localDate);

        log.info("Daily summary fetched: {}", result.isPresent() ? "found" : "not found");
        return result;
    }

    @Tool(name = "getMealsData",
          description = "Retrieves user's meal data for the given date range. Returns information about calories, macronutrients (protein, fat, carbohydrates), meal types and health ratings. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public MealsRangeSummaryResponse getMealsData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching meals data for device {} from {} to {}", deviceId, startDate, endDate);

        var start = LocalDate.parse(startDate);
        var end = LocalDate.parse(endDate);
        var result = mealsFacade.getRangeSummary(start, end);

        log.info("Meals data fetched: {} total meals over {} days", result.totalMealCount(), result.daysWithData());
        return result;
    }
}
