package com.healthassistant.assistant;

import com.healthassistant.bodymeasurements.api.BodyMeasurementsFacade;
import com.healthassistant.bodymeasurements.api.dto.BodyPart;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.mealcatalog.api.MealCatalogFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import com.healthassistant.healthevents.api.dto.payload.WeightMeasurementPayload;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.RecordMealRequest;
import com.healthassistant.meals.api.dto.UpdateMealRequest;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;


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
    private final HealthEventsFacade healthEventsFacade;
    private final MealCatalogFacade mealCatalogFacade;
    private final ObjectMapper objectMapper;
    private final AiMetricsRecorder aiMetrics;

    // ==================== READ TOOLS ====================

    @Tool(name = "getStepsData",
          description = "Retrieves user's step data for the given date range. Returns step count, distance, active hours and minutes. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getStepsData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching steps data for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

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
        log.info("Fetching sleep data for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

        return validateAndExecuteRangeQuery("getSleepData", startDate, endDate, (start, end) -> {
            var result = sleepFacade.getRangeSummary(deviceId, start, end);
            log.info("Sleep data fetched: {} total minutes over {} days", result.totalSleepMinutes(), result.daysWithData());
            return result;
        });
    }

    @Tool(name = "getWorkoutData",
          description = "Retrieves user's strength training data for the given date range. Returns a list of workouts with exercises, sets and volume. " +
                        "Each workout includes an eventId field that can be used with deleteWorkout. " +
                        "PARAMETERS: startDate and endDate must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getWorkoutData(String startDate, String endDate, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching workout data for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

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
        log.info("Fetching daily summary for device {} for date {}", maskDeviceId(deviceId), date);

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
        log.info("Fetching daily summary range for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

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
        log.info("Fetching meals data for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

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
        log.info("Fetching weight data for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

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
        log.info("Fetching body measurements data for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);

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
        log.info("Fetching energy requirements for device {} for date {}", maskDeviceId(deviceId), date);

        return validateAndExecuteSingleDateQuery("getEnergyRequirements", date, localDate -> {
            var result = mealsFacade.getEnergyRequirements(deviceId, localDate);
            log.info("Energy requirements fetched: {}", result.isPresent() ? "found" : "not available");
            return result.<Object>map(r -> r)
                    .orElseGet(() -> new ToolError("Energy requirements not available. Weight data may be missing."));
        });
    }

    @Tool(name = "getMealsDailyDetail",
          description = "Retrieves detailed list of individual meals for a specific day, including eventId for each meal. " +
                        "Use this BEFORE updateMeal or deleteMeal to get the eventId of the meal to modify. " +
                        "Returns each meal with: eventId, title, mealType, calories, protein, fat, carbs, healthRating. " +
                        "PARAMETER: date must be in ISO-8601 format (YYYY-MM-DD), e.g. '2025-11-24'.")
    public Object getMealsDailyDetail(String date, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Fetching meals daily detail for device {} for date {}", maskDeviceId(deviceId), date);

        return validateAndExecuteSingleDateQuery("getMealsDailyDetail", date, localDate -> {
            var result = mealsFacade.getDailyDetail(deviceId, localDate);
            log.info("Meals daily detail fetched: {} meals for {}", result.totalMealCount(), localDate);
            return result;
        });
    }

    @Tool(name = "searchMealCatalog",
          description = "Searches the user's personal meal catalog for products by name. " +
                        "The catalog auto-learns from meal imports and recordings. " +
                        "Use this BEFORE recording a meal when user mentions a product by name - if found, use exact nutritional values from catalog. " +
                        "Returns matching products with nutritional values (calories, protein, fat, carbs) and usage count. " +
                        "PARAMETER: query (String, required) - product name to search for, e.g. 'Skyr', 'baton proteinowy'.")
    public Object searchMealCatalog(String query, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Searching meal catalog for device {}: query='{}'", maskDeviceId(deviceId), sanitizeForLog(query));

        var sample = aiMetrics.startTimer();
        try {
            if (query == null || query.isBlank()) {
                aiMetrics.recordToolCall("searchMealCatalog", sample, "validation_error");
                return new ToolError("Query cannot be empty. Please provide a product name to search for.");
            }
            if (query.length() > 200) {
                aiMetrics.recordToolCall("searchMealCatalog", sample, "validation_error");
                return new ToolError("Query is too long (max 200 characters).");
            }

            var results = mealCatalogFacade.searchProducts(deviceId, query, 10);
            log.info("Meal catalog search returned {} results for query '{}'", results.size(), sanitizeForLog(query));
            aiMetrics.recordToolCall("searchMealCatalog", sample, "success");

            if (results.isEmpty()) {
                return new ToolError("No products found matching '" + sanitizeForLog(query) + "' in your catalog. You can estimate the values yourself.");
            }
            return results;
        } catch (Exception e) {
            log.error("Error searching meal catalog", e);
            aiMetrics.recordToolCall("searchMealCatalog", sample, "error");
            return new ToolError("Unable to search meal catalog. Please estimate the values yourself.");
        }
    }

    // ==================== MUTATION TOOLS ====================

    @Tool(name = "recordMeal",
          description = "Records a new meal for the user. Use when user tells you what they ate. " +
                        "You can estimate macronutrients if user doesn't provide exact values (but tell the user you estimated). " +
                        "PARAMETERS: " +
                        "title (String, required) - name/description of the meal, e.g. 'Chicken with rice'. " +
                        "mealType (String, required) - one of: BREAKFAST, BRUNCH, LUNCH, DINNER, SNACK, DESSERT, DRINK. " +
                        "caloriesKcal (String, required) - calories in kcal, e.g. '500'. " +
                        "proteinGrams (String, required) - protein in grams, e.g. '40'. " +
                        "fatGrams (String, required) - fat in grams, e.g. '15'. " +
                        "carbohydratesGrams (String, required) - carbohydrates in grams, e.g. '55'. " +
                        "healthRating (String, required) - one of: VERY_HEALTHY, HEALTHY, NEUTRAL, UNHEALTHY, VERY_UNHEALTHY. " +
                        "occurredAt (String, optional) - ISO-8601 UTC timestamp, e.g. '2025-11-24T12:30:00Z'. If not provided, defaults to now.")
    public Object recordMeal(String title, String mealType, String caloriesKcal, String proteinGrams,
                             String fatGrams, String carbohydratesGrams, String healthRating,
                             String occurredAt, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Recording meal for device {}: {}", maskDeviceId(deviceId), sanitizeForLog(title));

        return validateAndExecuteMutation("recordMeal", () -> {
            validateTitle(title);
            var parsedMealType = parseEnum(MealType.class, mealType, "mealType");
            var parsedHealthRating = parseEnum(HealthRating.class, healthRating, "healthRating");
            var parsedCalories = parseNonNegativeInt(caloriesKcal, "caloriesKcal");
            var parsedProtein = parseNonNegativeInt(proteinGrams, "proteinGrams");
            var parsedFat = parseNonNegativeInt(fatGrams, "fatGrams");
            var parsedCarbs = parseNonNegativeInt(carbohydratesGrams, "carbohydratesGrams");
            var parsedOccurredAt = occurredAt != null && !occurredAt.isBlank() ? parseInstant(occurredAt, "occurredAt") : null;

            var request = new RecordMealRequest(title, parsedMealType, parsedCalories,
                    parsedProtein, parsedFat, parsedCarbs, parsedHealthRating, parsedOccurredAt);

            var result = mealsFacade.recordMeal(deviceId, request);
            log.info("Meal recorded: eventId={}, title={}", result.eventId(), sanitizeForLog(title));
            saveToCatalogQuietly(deviceId, title, parsedMealType.name(), parsedCalories,
                    parsedProtein, parsedFat, parsedCarbs, parsedHealthRating.name());
            return new MutationSuccess("Meal recorded successfully", result);
        });
    }

    @Tool(name = "updateMeal",
          description = "Updates an existing meal. Use getMealsDailyDetail first to get the eventId. " +
                        "PARAMETERS: " +
                        "eventId (String, required) - the eventId of the meal to update (from getMealsDailyDetail). " +
                        "title (String, required) - new name/description of the meal. " +
                        "mealType (String, required) - one of: BREAKFAST, BRUNCH, LUNCH, DINNER, SNACK, DESSERT, DRINK. " +
                        "caloriesKcal (String, required) - new calories in kcal. " +
                        "proteinGrams (String, required) - new protein in grams. " +
                        "fatGrams (String, required) - new fat in grams. " +
                        "carbohydratesGrams (String, required) - new carbohydrates in grams. " +
                        "healthRating (String, required) - one of: VERY_HEALTHY, HEALTHY, NEUTRAL, UNHEALTHY, VERY_UNHEALTHY.")
    public Object updateMeal(String eventId, String title, String mealType, String caloriesKcal,
                             String proteinGrams, String fatGrams, String carbohydratesGrams,
                             String healthRating, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Updating meal {} for device {}", sanitizeForLog(eventId), maskDeviceId(deviceId));

        return validateAndExecuteMutation("updateMeal", () -> {
            validateEventId(eventId);
            validateTitle(title);
            var parsedMealType = parseEnum(MealType.class, mealType, "mealType");
            var parsedHealthRating = parseEnum(HealthRating.class, healthRating, "healthRating");
            var parsedCalories = parseNonNegativeInt(caloriesKcal, "caloriesKcal");
            var parsedProtein = parseNonNegativeInt(proteinGrams, "proteinGrams");
            var parsedFat = parseNonNegativeInt(fatGrams, "fatGrams");
            var parsedCarbs = parseNonNegativeInt(carbohydratesGrams, "carbohydratesGrams");

            var request = new UpdateMealRequest(title, parsedMealType, parsedCalories,
                    parsedProtein, parsedFat, parsedCarbs, parsedHealthRating, null);

            var result = mealsFacade.updateMeal(deviceId, eventId, request);
            log.info("Meal updated: eventId={}", result.eventId());
            return new MutationSuccess("Meal updated successfully", result);
        });
    }

    @Tool(name = "deleteMeal",
          description = "Deletes a meal. Use getMealsDailyDetail first to get the eventId. " +
                        "IMPORTANT: Always confirm with the user before deleting. " +
                        "PARAMETER: eventId (String, required) - the eventId of the meal to delete.")
    public Object deleteMeal(String eventId, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Deleting meal {} for device {}", sanitizeForLog(eventId), maskDeviceId(deviceId));

        return validateAndExecuteMutation("deleteMeal", () -> {
            validateEventId(eventId);
            mealsFacade.deleteMeal(deviceId, eventId);
            log.info("Meal deleted: eventId={}", sanitizeForLog(eventId));
            return new MutationSuccess("Meal deleted successfully", null);
        });
    }

    @Tool(name = "recordWeight",
          description = "Records a weight measurement for the user. " +
                        "PARAMETERS: " +
                        "weightKg (String, required) - weight in kilograms, e.g. '82.5'. Must be between 1 and 500. " +
                        "measuredAt (String, optional) - ISO-8601 UTC timestamp, e.g. '2025-11-24T07:00:00Z'. If not provided, defaults to now.")
    public Object recordWeight(String weightKg, String measuredAt, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Recording weight for device {}", maskDeviceId(deviceId));

        return validateAndExecuteMutation("recordWeight", () -> {
            var parsedWeight = parsePositiveBigDecimal(weightKg, "weightKg");
            if (parsedWeight.compareTo(BigDecimal.ONE) < 0 || parsedWeight.compareTo(new BigDecimal("500")) > 0) {
                throw new IllegalArgumentException("weightKg must be between 1 and 500");
            }

            var timestamp = measuredAt != null && !measuredAt.isBlank()
                    ? parseInstant(measuredAt, "measuredAt")
                    : Instant.now();

            var measurementId = "assistant-weight-" + UUID.randomUUID();
            var payload = new WeightMeasurementPayload(
                    measurementId, timestamp, null, parsedWeight,
                    null, null, null, null, null,
                    null, null, null, null, null,
                    null, null, null, null, null, null, null, "ASSISTANT"
            );

            var idempotencyKey = deviceId + "|weight|" + measurementId;
            var command = new StoreHealthEventsCommand(
                    List.of(new StoreHealthEventsCommand.EventEnvelope(
                            new IdempotencyKey(idempotencyKey),
                            "WeightMeasurementRecorded.v1",
                            timestamp,
                            payload
                    )),
                    new DeviceId(deviceId)
            );

            var result = healthEventsFacade.storeHealthEvents(command);
            validateStoreResult(result, "weight measurement");

            log.info("Weight recorded: {} kg", parsedWeight);
            return new MutationSuccess("Weight measurement recorded: " + parsedWeight + " kg",
                    new WeightRecordResult(result.results().getFirst().eventId().value(), parsedWeight, timestamp));
        });
    }

    @Tool(name = "recordWorkout",
          description = "Records a strength training workout. " +
                        "PARAMETERS: " +
                        "performedAt (String, required) - ISO-8601 UTC timestamp when workout was performed, e.g. '2025-11-24T18:00:00Z'. " +
                        "note (String, optional) - workout note/description, e.g. 'Chest and triceps'. " +
                        "exercisesJson (String, required) - JSON array of exercises. Each exercise must have: " +
                        "name (String), orderInWorkout (int), sets (array of {setNumber, weightKg, reps, isWarmup}). " +
                        "Example: '[{\"name\":\"Bench Press\",\"orderInWorkout\":1,\"sets\":[{\"setNumber\":1,\"weightKg\":80.0,\"reps\":10,\"isWarmup\":false}]}]'")
    public Object recordWorkout(String performedAt, String note, String exercisesJson, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Recording workout for device {} at {}", maskDeviceId(deviceId), sanitizeForLog(performedAt));

        return validateAndExecuteMutation("recordWorkout", () -> {
            var timestamp = parseInstant(performedAt, "performedAt");
            validateWorkoutTimestamp(timestamp);

            List<WorkoutPayload.Exercise> exercises;
            try {
                exercises = objectMapper.readValue(exercisesJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, WorkoutPayload.Exercise.class));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid exercisesJson format. Please provide a valid JSON array of exercises.", e);
            }

            if (exercises.isEmpty()) {
                throw new IllegalArgumentException("exercises cannot be empty");
            }

            var workoutId = "assistant-workout-" + UUID.randomUUID();
            var payload = new WorkoutPayload(workoutId, timestamp, "ASSISTANT", note, exercises);

            var idempotencyKey = deviceId + "|workout|" + workoutId;
            var command = new StoreHealthEventsCommand(
                    List.of(new StoreHealthEventsCommand.EventEnvelope(
                            new IdempotencyKey(idempotencyKey),
                            "WorkoutRecorded.v1",
                            timestamp,
                            payload
                    )),
                    new DeviceId(deviceId)
            );

            var result = healthEventsFacade.storeHealthEvents(command);
            validateStoreResult(result, "workout");

            int totalSets = exercises.stream().mapToInt(e -> e.sets().size()).sum();
            log.info("Workout recorded: {} exercises, {} sets", exercises.size(), totalSets);
            return new MutationSuccess("Workout recorded successfully with " + exercises.size() + " exercises and " + totalSets + " sets",
                    new WorkoutRecordResult(result.results().getFirst().eventId().value(), workoutId, timestamp, exercises.size(), totalSets));
        });
    }

    @Tool(name = "deleteWorkout",
          description = "Deletes a workout. Use getWorkoutData first to find the workout and its eventId. " +
                        "IMPORTANT: Always confirm with the user before deleting. " +
                        "PARAMETER: eventId (String, required) - the eventId of the workout to delete (from getWorkoutData response).")
    public Object deleteWorkout(String eventId, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Deleting workout {} for device {}", sanitizeForLog(eventId), maskDeviceId(deviceId));

        return validateAndExecuteMutation("deleteWorkout", () -> {
            validateEventId(eventId);
            workoutFacade.deleteWorkout(deviceId, eventId);
            log.info("Workout deleted: eventId={}", sanitizeForLog(eventId));
            return new MutationSuccess("Workout deleted successfully", null);
        });
    }

    @Tool(name = "recordSleep",
          description = "Records a sleep session for the user. " +
                        "PARAMETERS: " +
                        "sleepStart (String, required) - ISO-8601 UTC timestamp when sleep started, e.g. '2025-11-23T23:00:00Z'. " +
                        "sleepEnd (String, required) - ISO-8601 UTC timestamp when sleep ended, e.g. '2025-11-24T07:00:00Z'. Must be after sleepStart.")
    public Object recordSleep(String sleepStart, String sleepEnd, ToolContext toolContext) {
        var deviceId = getDeviceId(toolContext);
        log.info("Recording sleep for device {}", maskDeviceId(deviceId));

        return validateAndExecuteMutation("recordSleep", () -> {
            var parsedStart = parseInstant(sleepStart, "sleepStart");
            var parsedEnd = parseInstant(sleepEnd, "sleepEnd");

            if (!parsedEnd.isAfter(parsedStart)) {
                throw new IllegalArgumentException("sleepEnd must be after sleepStart");
            }

            var totalMinutes = (int) Duration.between(parsedStart, parsedEnd).toMinutes();
            if (totalMinutes > 1440) {
                throw new IllegalArgumentException("Sleep session cannot exceed 24 hours (1440 minutes)");
            }
            var sleepId = "assistant-sleep-" + UUID.randomUUID();

            var payload = new SleepSessionPayload(sleepId, parsedStart, parsedEnd, totalMinutes, "ASSISTANT");

            var idempotencyKey = deviceId + "|sleep|" + sleepId;
            var command = new StoreHealthEventsCommand(
                    List.of(new StoreHealthEventsCommand.EventEnvelope(
                            new IdempotencyKey(idempotencyKey),
                            "SleepSessionRecorded.v1",
                            parsedEnd,
                            payload
                    )),
                    new DeviceId(deviceId)
            );

            var result = healthEventsFacade.storeHealthEvents(command);
            validateStoreResult(result, "sleep session");

            log.info("Sleep recorded: {} minutes", totalMinutes);
            return new MutationSuccess("Sleep session recorded: " + totalMinutes + " minutes (" + totalMinutes / 60 + "h " + totalMinutes % 60 + "min)",
                    new SleepRecordResult(result.results().getFirst().eventId().value(), parsedStart, parsedEnd, totalMinutes));
        });
    }

    // ==================== HELPER METHODS ====================

    private <T> Object validateAndExecuteMutation(String toolName, MutationCommand<T> command) {
        var sample = aiMetrics.startTimer();
        try {
            var result = command.execute();
            aiMetrics.recordToolCall(toolName, sample, "success");
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("Validation error in mutation {}: {}", toolName, e.getMessage());
            aiMetrics.recordToolCall(toolName, sample, "validation_error");
            return new ToolError(e.getMessage());
        } catch (Exception e) {
            log.error("Error executing mutation {}", toolName, e);
            aiMetrics.recordToolCall(toolName, sample, "error");
            return new ToolError(mapMutationErrorMessage(e));
        }
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

    // ==================== PARSING HELPERS ====================

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": '" + value + "'. Valid values: " +
                    String.join(", ", java.util.Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList()), e);
        }
    }

    private int parseNonNegativeInt(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": '" + value + "'. Must be a non-negative integer.", e);
        }
    }

    private BigDecimal parsePositiveBigDecimal(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        try {
            var parsed = new BigDecimal(value.trim());
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": '" + value + "'. Must be a positive number.", e);
        }
    }

    private Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": '" + value + "'. Must be ISO-8601 UTC format, e.g. '2025-11-24T12:00:00Z'.", e);
        }
    }

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_WORKOUT_BACKDATE_DAYS = 30;

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be empty");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title cannot exceed " + MAX_TITLE_LENGTH + " characters");
        }
    }

    private void validateWorkoutTimestamp(Instant timestamp) {
        var now = Instant.now();
        if (timestamp.isAfter(now)) {
            throw new IllegalArgumentException("Workout date cannot be in the future");
        }
        long daysBetween = ChronoUnit.DAYS.between(timestamp, now);
        if (daysBetween > MAX_WORKOUT_BACKDATE_DAYS) {
            throw new IllegalArgumentException("Workout date cannot be more than " + MAX_WORKOUT_BACKDATE_DAYS + " days in the past");
        }
    }

    private void validateEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId cannot be empty");
        }
        if (eventId.length() > 64) {
            throw new IllegalArgumentException("eventId is too long");
        }
    }

    private void validateStoreResult(StoreHealthEventsResult result, String entityName) {
        if (result.results().isEmpty() ||
                result.results().getFirst().status() == StoreHealthEventsResult.EventStatus.invalid) {
            var errorDetail = result.results().isEmpty() ? "No result" :
                    result.results().getFirst().error() != null ?
                            result.results().getFirst().error().message() : "Unknown error";
            throw new IllegalStateException("Failed to store " + entityName + ": " + errorDetail);
        }
    }

    private String mapMutationErrorMessage(Exception e) {
        var message = e.getMessage();
        if (message == null) return "An unexpected error occurred. Please try again.";
        if (message.contains("not found") || message.contains("NotFoundException")) return "The requested item was not found.";
        if (message.contains("not a workout") || message.contains("not a meal")) return "The specified event is not the correct type.";
        return "An error occurred while processing your request. Please try again.";
    }

    private void saveToCatalogQuietly(String deviceId, String title, String mealType,
                                      int caloriesKcal, int proteinGrams, int fatGrams,
                                      int carbohydratesGrams, String healthRating) {
        try {
            mealCatalogFacade.saveProduct(deviceId,
                    new com.healthassistant.mealcatalog.api.dto.SaveProductRequest(
                            title, mealType, caloriesKcal, proteinGrams, fatGrams,
                            carbohydratesGrams, healthRating
                    ));
        } catch (Exception e) {
            log.warn("Failed to save meal to catalog: {}", e.getMessage());
        }
    }

    private static String sanitizeForLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\\r\\n\\t]", "_").substring(0, Math.min(input.length(), 100));
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) return "***";
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    // ==================== FUNCTIONAL INTERFACES & RECORDS ====================

    @FunctionalInterface
    private interface DateRangeQuery<T> {
        T execute(LocalDate start, LocalDate end);
    }

    @FunctionalInterface
    private interface SingleDateQuery<T> {
        T execute(LocalDate date);
    }

    @FunctionalInterface
    private interface MutationCommand<T> {
        T execute();
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

    record MutationSuccess(String message, Object data) {}

    record WeightRecordResult(String eventId, BigDecimal weightKg, Instant measuredAt) {}

    record WorkoutRecordResult(String eventId, String workoutId, Instant performedAt, int exerciseCount, int totalSets) {}

    record SleepRecordResult(String eventId, Instant sleepStart, Instant sleepEnd, int totalMinutes) {}
}
