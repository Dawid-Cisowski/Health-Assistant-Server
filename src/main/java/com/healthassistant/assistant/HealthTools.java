package com.healthassistant.assistant;

import com.healthassistant.bodymeasurements.api.BodyMeasurementsFacade;
import com.healthassistant.bodymeasurements.api.dto.BodyPart;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.weight.api.WeightFacade;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.config.AiMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class HealthTools {

    static final String TOOL_CONTEXT_DEVICE_ID = "deviceId";

    private static final int MAX_DATE_RANGE_DAYS = 365;
    private static final int MAX_HISTORICAL_YEARS = 5;

    private final StepsFacade stepsFacade;
    private final SleepFacade sleepFacade;
    private final WorkoutFacade workoutFacade;
    private final DailySummaryFacade dailySummaryFacade;
    private final MealsFacade mealsFacade;
    private final WeightFacade weightFacade;
    private final BodyMeasurementsFacade bodyMeasurementsFacade;
    private final AiMetricsRecorder aiMetrics;

    @Tool(name = "getStepsData",
          description = "Retrieves user's step data for the given date range. Returns step count, distance, active hours and minutes. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getStepsData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching steps data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getStepsData", startDate, endDate, (start, end) -> {
            var result = stepsFacade.getRangeSummary(deviceId, start, end);
            log.info("Steps data fetched: {} total steps over {} days", result.totalSteps(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getSleepData",
          description = "Retrieves user's sleep data for the given date range. Returns information about sleep duration, quality and sleep sessions. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getSleepData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching sleep data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getSleepData", startDate, endDate, (start, end) -> {
            var result = sleepFacade.getRangeSummary(deviceId, start, end);
            log.info("Sleep data fetched: {} total minutes over {} days", result.totalSleepMinutes(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getWorkoutData",
          description = "Retrieves user's strength training data for the given date range. Returns a list of workouts with exercises, sets and volume. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getWorkoutData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching workout data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getWorkoutData", startDate, endDate, (start, end) -> {
            var result = workoutFacade.getWorkoutsByDateRange(deviceId, start, end);
            log.info("Workout data fetched: {} workouts", result.size());
            return result;
        });
    }

    @Tool(name = "getDailySummary",
          description = "Retrieves complete daily summary for a SINGLE day. Contains all data: activity (steps, calories), sleep, heart rate, meals and workouts. " +
                        "Use getDailySummaryRange for multi-day queries (last week, last month). " +
                        "PARAMETER: date must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getDailySummary(String date, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching daily summary for device {} for date {}", deviceId, date);

        return validateAndExecuteSingleDateQuery("getDailySummary", date, localDate -> {
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
    public Object getDailySummaryRange(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching daily summary range for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getDailySummaryRange", startDate, endDate, (start, end) -> {
            var result = dailySummaryFacade.getRangeSummary(deviceId, start, end);
            log.info("Daily summary range fetched: {} days with data", result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getMealsData",
          description = "Retrieves user's meal data for the given date range. Returns information about calories, macronutrients (protein, fat, carbohydrates), meal types and health ratings. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getMealsData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching meals data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getMealsData", startDate, endDate, (start, end) -> {
            var result = mealsFacade.getRangeSummary(deviceId, start, end);
            log.info("Meals data fetched: {} total meals over {} days", result.totalMealCount(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getWeightData",
          description = "Retrieves user's weight and body composition data for the given date range. " +
                        "Returns weight, BMI, body fat %, muscle %, hydration, bone mass, BMR, visceral fat level, metabolic age, and trend analysis. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getWeightData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching weight data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getWeightData", startDate, endDate, (start, end) -> {
            var result = weightFacade.getRangeSummary(deviceId, start, end);
            log.info("Weight data fetched: {} measurements over {} days", result.measurementCount(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getBodyMeasurementsData",
          description = "Retrieves user's body measurements data (dimensions like biceps, waist, chest, thighs, etc.) for the given date range. " +
                        "Returns measurements in centimeters with changes vs previous measurements. " +
                        "Use this for questions about: body dimensions, muscle growth, circumference changes, tape measurements. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getBodyMeasurementsData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching body measurements data for device {} from {} to {}", deviceId, startDate, endDate);

        return validateAndExecuteRangeQuery("getBodyMeasurementsData", startDate, endDate, (start, end) -> {
            var result = bodyMeasurementsFacade.getRangeSummary(deviceId, start, end);
            log.info("Body measurements data fetched: {} measurements over {} days", result.measurementCount(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getBodyPartHistory",
          description = "Retrieves measurement history for a SPECIFIC body part for charting/trend analysis. " +
                        "Returns time-series data points with min, max, change, and change percentage. " +
                        "Use this for questions like: 'How have my biceps grown?', 'Show my waist trend', 'Has my chest size increased?'. " +
                        "PARAMETERS: bodyPart must be one of: biceps-left, biceps-right, forearm-left, forearm-right, chest, waist, abdomen, hips, neck, shoulders, thigh-left, thigh-right, calf-left, calf-right. " +
                        "startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getBodyPartHistory(String bodyPart, String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching body part history for {} from {} to {}", bodyPart, startDate, endDate);

        return validateAndExecuteRangeQuery("getBodyPartHistory", startDate, endDate, (start, end) -> {
            BodyPart part;
            try {
                part = BodyPart.fromValue(bodyPart);
            } catch (IllegalArgumentException e) {
                return new ToolError("Invalid body part: '" + bodyPart + "'. Valid options: biceps-left, biceps-right, forearm-left, forearm-right, chest, waist, abdomen, hips, neck, shoulders, thigh-left, thigh-right, calf-left, calf-right");
            }

            var result = bodyMeasurementsFacade.getBodyPartHistory(deviceId, part, start, end);
            log.info("Body part history fetched: {} data points", result.dataPoints().size());
            return result;
        });
    }

    @Tool(name = "getEnergyRequirements",
          description = "Retrieves user's daily energy requirements and macro targets for a specific date. " +
                        "Returns target calories, protein/fat/carbs targets, already consumed amounts, and remaining to eat. " +
                        "Use this to answer questions about: recommended calories, macro goals, how much more to eat, nutrition targets. " +
                        "PARAMETER: date must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getEnergyRequirements(String date, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching energy requirements for device {} for date {}", deviceId, date);

        return validateAndExecuteSingleDateQuery("getEnergyRequirements", date, localDate -> {
            var result = mealsFacade.getEnergyRequirements(deviceId, localDate);
            log.info("Energy requirements fetched: {}", result.isPresent() ? "found" : "not available");
            return result.<Object>map(r -> r)
                    .orElseGet(() -> new ToolError("Energy requirements not available. Weight data may be missing."));
        });
    }

    private <T> Object validateAndExecuteRangeQuery(String toolName, String startDateStr, String endDateStr, DateRangeQuery<T> query) {
        var sample = aiMetrics.startTimer();

        var startDateResult = parseDate(startDateStr);
        if (startDateResult instanceof ToolError error) {
            aiMetrics.recordToolCall(toolName, sample, "validation_error");
            return error;
        }

        var endDateResult = parseDate(endDateStr);
        if (endDateResult instanceof ToolError error) {
            aiMetrics.recordToolCall(toolName, sample, "validation_error");
            return error;
        }

        var start = (LocalDate) startDateResult;
        var end = (LocalDate) endDateResult;

        var validationError = validateDateRange(start, end);
        if (validationError != null) {
            aiMetrics.recordToolCall(toolName, sample, "validation_error");
            return validationError;
        }

        try {
            var result = query.execute(start, end);
            aiMetrics.recordToolCall(toolName, sample, "success");
            return result;
        } catch (Exception e) {
            log.error("Error executing range query from {} to {}", start, end, e);
            aiMetrics.recordToolCall(toolName, sample, "error");
            return new ToolError("Unable to retrieve data. Please try again later.");
        }
    }

    private <T> Object validateAndExecuteSingleDateQuery(String toolName, String dateStr, SingleDateQuery<T> query) {
        var sample = aiMetrics.startTimer();

        var dateResult = parseDate(dateStr);
        if (dateResult instanceof ToolError error) {
            aiMetrics.recordToolCall(toolName, sample, "validation_error");
            return error;
        }

        var date = (LocalDate) dateResult;

        var validationError = validateSingleDate(date);
        if (validationError != null) {
            aiMetrics.recordToolCall(toolName, sample, "validation_error");
            return validationError;
        }

        try {
            var result = query.execute(date);
            aiMetrics.recordToolCall(toolName, sample, "success");
            return result;
        } catch (Exception e) {
            log.error("Error executing single date query for {}", date, e);
            aiMetrics.recordToolCall(toolName, sample, "error");
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

    private String getDeviceId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new IllegalStateException("ToolContext or context map is null - deviceId not available");
        }
        var deviceId = toolContext.getContext().get(TOOL_CONTEXT_DEVICE_ID);
        if (deviceId == null) {
            throw new IllegalStateException("deviceId not found in ToolContext");
        }
        return (String) deviceId;
    }

    record ToolError(String message) {}
}
