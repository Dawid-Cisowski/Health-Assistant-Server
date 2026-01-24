package com.healthassistant.assistant;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;
import com.healthassistant.weight.api.WeightFacade;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class HealthTools {

    private static final int MAX_DATE_RANGE_DAYS = 365;
    private static final int MAX_HISTORICAL_YEARS = 5;

    private final StepsFacade stepsFacade;
    private final SleepFacade sleepFacade;
    private final WorkoutFacade workoutFacade;
    private final DailySummaryFacade dailySummaryFacade;
    private final MealsFacade mealsFacade;
    private final WeightFacade weightFacade;

    @Tool(name = "getStepsData",
          description = "Retrieves user's step data for the given date range. Returns step count, distance, active hours and minutes. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getStepsData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching steps data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery(startDate, endDate, (start, end) -> {
            var result = stepsFacade.getRangeSummary(deviceId, start, end);
            log.info("Steps data fetched: {} total steps over {} days", result.totalSteps(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getSleepData",
          description = "Retrieves user's sleep data for the given date range. Returns information about sleep duration, quality and sleep sessions. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getSleepData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching sleep data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery(startDate, endDate, (start, end) -> {
            var result = sleepFacade.getRangeSummary(deviceId, start, end);
            log.info("Sleep data fetched: {} total minutes over {} days", result.totalSleepMinutes(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getWorkoutData",
          description = "Retrieves user's strength training data for the given date range. Returns a list of workouts with exercises, sets and volume. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getWorkoutData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching workout data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery(startDate, endDate, (start, end) -> {
            var result = workoutFacade.getWorkoutsByDateRange(deviceId, start, end);
            log.info("Workout data fetched: {} workouts", result.size());
            return result;
        });
    }

    @Tool(name = "getDailySummary",
          description = "Retrieves complete daily summary for a SINGLE day. Contains all data: activity (steps, calories), sleep, heart rate, meals and workouts. " +
                        "Use getDailySummaryRange for multi-day queries (last week, last month). " +
                        "PARAMETER: date must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getDailySummary(String date) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching daily summary for device {} for date {}", deviceId, date);

        return validateAndExecuteSingleDateQuery(date, localDate -> {
            var result = dailySummaryFacade.getDailySummary(deviceId, localDate);
            if (result.isPresent()) {
                var summary = result.get();
                log.info("Daily summary fetched for {}: steps={}, calories={}, sleep={} min, meals={}, workouts={}",
                    localDate,
                    summary.getTotalSteps(),
                    summary.getActiveCalories(),
                    summary.getTotalSleepMinutes(),
                    summary.getMealCount(),
                    summary.getWorkoutCount()
                );
            } else {
                log.info("Daily summary not found for date {}", localDate);
            }
            return result;
        });
    }

    @Tool(name = "getDailySummaryRange",
          description = "Retrieves aggregated daily summaries for a DATE RANGE (week, month, etc.). " +
                        "Returns totals and averages for: active calories burned, steps, distance, sleep, heart rate, meals. " +
                        "USE THIS for questions about 'last week', 'last month', or any multi-day period. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getDailySummaryRange(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching daily summary range for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery(startDate, endDate, (start, end) -> {
            var result = dailySummaryFacade.getRangeSummary(deviceId, start, end);
            log.info("Daily summary range fetched: {} days with data", result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getMealsData",
          description = "Retrieves user's meal data for the given date range. Returns information about calories, macronutrients (protein, fat, carbohydrates), meal types and health ratings. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getMealsData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching meals data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery(startDate, endDate, (start, end) -> {
            var result = mealsFacade.getRangeSummary(deviceId, start, end);
            log.info("Meals data fetched: {} total meals over {} days", result.totalMealCount(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getWeightData",
          description = "Retrieves user's weight and body composition data for the given date range. " +
                        "Returns weight, BMI, body fat %, muscle %, hydration, bone mass, BMR, visceral fat level, metabolic age, and trend analysis. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getWeightData(String startDate, String endDate) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching weight data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery(startDate, endDate, (start, end) -> {
            var result = weightFacade.getRangeSummary(deviceId, start, end);
            log.info("Weight data fetched: {} measurements over {} days", result.measurementCount(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getEnergyRequirements",
          description = "Retrieves user's daily energy requirements and macro targets for a specific date. " +
                        "Returns target calories, protein/fat/carbs targets, already consumed amounts, and remaining to eat. " +
                        "Use this to answer questions about: recommended calories, macro goals, how much more to eat, nutrition targets. " +
                        "PARAMETER: date must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getEnergyRequirements(String date) {
        var deviceId = AssistantContext.getDeviceId();
        log.info("Fetching energy requirements for device {} for date {}", deviceId, date);

        return validateAndExecuteSingleDateQuery(date, localDate -> {
            var result = mealsFacade.getEnergyRequirements(deviceId, localDate);
            log.info("Energy requirements fetched: {}", result.isPresent() ? "found" : "not available");
            return result.<Object>map(r -> r)
                    .orElseGet(() -> new ToolError("Energy requirements not available. Weight data may be missing."));
        });
    }

    private <T> Object validateAndExecuteRangeQuery(String startDateStr, String endDateStr, DateRangeQuery<T> query) {
        var startDateResult = parseDate(startDateStr);
        if (startDateResult instanceof ToolError error) {
            return error;
        }

        var endDateResult = parseDate(endDateStr);
        if (endDateResult instanceof ToolError error) {
            return error;
        }

        var start = (LocalDate) startDateResult;
        var end = (LocalDate) endDateResult;

        var validationError = validateDateRange(start, end);
        if (validationError != null) {
            return validationError;
        }

        try {
            return query.execute(start, end);
        } catch (Exception e) {
            log.error("Error executing range query from {} to {}", start, end, e);
            return new ToolError("Unable to retrieve data. Please try again later.");
        }
    }

    private <T> Object validateAndExecuteSingleDateQuery(String dateStr, SingleDateQuery<T> query) {
        var dateResult = parseDate(dateStr);
        if (dateResult instanceof ToolError error) {
            return error;
        }

        var date = (LocalDate) dateResult;

        var validationError = validateSingleDate(date);
        if (validationError != null) {
            return validationError;
        }

        try {
            return query.execute(date);
        } catch (Exception e) {
            log.error("Error executing single date query for {}", date, e);
            return new ToolError("Unable to retrieve data. Please try again later.");
        }
    }

    private Object parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            log.warn("Empty date provided to tool");
            return new ToolError("Date cannot be empty. Please provide a date in YYYY-MM-DD format.");
        }

        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}", dateStr);
            return new ToolError("Invalid date format: '" + dateStr + "'. Please use ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.");
        }
    }

    private ToolError validateDateRange(LocalDate start, LocalDate end) {
        var today = LocalDate.now();

        if (start.isAfter(end)) {
            log.warn("Start date {} is after end date {}", start, end);
            return new ToolError("Start date cannot be after end date. Please swap the dates.");
        }

        if (end.isAfter(today)) {
            log.warn("End date {} is in the future", end);
            return new ToolError("Cannot query future dates. The end date must be today (" + today + ") or earlier.");
        }

        var daysBetween = ChronoUnit.DAYS.between(start, end);
        if (daysBetween > MAX_DATE_RANGE_DAYS) {
            log.warn("Date range {} to {} exceeds {} days", start, end, MAX_DATE_RANGE_DAYS);
            return new ToolError("Date range cannot exceed " + MAX_DATE_RANGE_DAYS + " days. Please narrow down the date range.");
        }

        var maxHistoricalDate = today.minusYears(MAX_HISTORICAL_YEARS);
        if (start.isBefore(maxHistoricalDate)) {
            log.warn("Start date {} is more than {} years ago", start, MAX_HISTORICAL_YEARS);
            return new ToolError("Cannot query data older than " + MAX_HISTORICAL_YEARS + " years. Earliest allowed date is " + maxHistoricalDate + ".");
        }

        return null;
    }

    private ToolError validateSingleDate(LocalDate date) {
        var today = LocalDate.now();

        if (date.isAfter(today)) {
            log.warn("Date {} is in the future", date);
            return new ToolError("Cannot query future dates. The date must be today (" + today + ") or earlier.");
        }

        var maxHistoricalDate = today.minusYears(MAX_HISTORICAL_YEARS);
        if (date.isBefore(maxHistoricalDate)) {
            log.warn("Date {} is more than {} years ago", date, MAX_HISTORICAL_YEARS);
            return new ToolError("Cannot query data older than " + MAX_HISTORICAL_YEARS + " years. Earliest allowed date is " + maxHistoricalDate + ".");
        }

        return null;
    }

    @FunctionalInterface
    private interface DateRangeQuery<T> {
        T execute(LocalDate start, LocalDate end);
    }

    @FunctionalInterface
    private interface SingleDateQuery<T> {
        T execute(LocalDate date);
    }

    record ToolError(String message) {}
}
