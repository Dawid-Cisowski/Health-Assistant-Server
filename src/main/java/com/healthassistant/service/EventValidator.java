package com.healthassistant.service;

import com.healthassistant.dto.EventEnvelope;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates event payloads based on event type
 */
@Component
public class EventValidator {

    private static final Set<String> VALID_EVENT_TYPES = Set.of(
        "StepsBucketedRecorded.v1",
        "HeartRateSummaryRecorded.v1",
        "SleepSessionRecorded.v1",
        "ActiveCaloriesBurnedRecorded.v1",
        "ActiveMinutesRecorded.v1",
        "WorkoutSessionImported.v1",
        "SetPerformedImported.v1",
        "MealLoggedEstimated.v1"
    );

    public List<String> validate(EventEnvelope envelope) {
        List<String> errors = new ArrayList<>();

        // Validate event type
        if (!VALID_EVENT_TYPES.contains(envelope.getType())) {
            errors.add("Invalid event type: " + envelope.getType());
            return errors; // Don't proceed with payload validation
        }

        Map<String, Object> payload = envelope.getPayload();
        if (payload == null || payload.isEmpty()) {
            errors.add("Payload cannot be empty");
            return errors;
        }

        // Validate payload based on type
        switch (envelope.getType()) {
            case "StepsBucketedRecorded.v1" -> validateStepsBucketed(payload, errors);
            case "HeartRateSummaryRecorded.v1" -> validateHeartRateSummary(payload, errors);
            case "SleepSessionRecorded.v1" -> validateSleepSession(payload, errors);
            case "ActiveCaloriesBurnedRecorded.v1" -> validateActiveCaloriesBurned(payload, errors);
            case "ActiveMinutesRecorded.v1" -> validateActiveMinutes(payload, errors);
            case "WorkoutSessionImported.v1" -> validateWorkoutSession(payload, errors);
            case "SetPerformedImported.v1" -> validateSetPerformed(payload, errors);
            case "MealLoggedEstimated.v1" -> validateMealLogged(payload, errors);
        }

        return errors;
    }

    private void validateStepsBucketed(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "count", errors);
        requireField(payload, "originPackage", errors);
        
        if (payload.containsKey("count")) {
            validateNonNegativeNumber(payload.get("count"), "count", errors);
        }
    }

    private void validateHeartRateSummary(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "avg", errors);
        requireField(payload, "min", errors);
        requireField(payload, "max", errors);
        requireField(payload, "samples", errors);
        requireField(payload, "originPackage", errors);
        
        validateNonNegativeNumber(payload.get("avg"), "avg", errors);
        validateNonNegativeNumber(payload.get("min"), "min", errors);
        validateNonNegativeNumber(payload.get("max"), "max", errors);
        validatePositiveNumber(payload.get("samples"), "samples", errors);
    }

    private void validateSleepSession(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "sleepStart", errors);
        requireField(payload, "sleepEnd", errors);
        requireField(payload, "totalMinutes", errors);
        requireField(payload, "originPackage", errors);
        
        validateNonNegativeNumber(payload.get("totalMinutes"), "totalMinutes", errors);
    }

    private void validateActiveCaloriesBurned(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "energyKcal", errors);
        requireField(payload, "originPackage", errors);
        
        validateNonNegativeNumber(payload.get("energyKcal"), "energyKcal", errors);
    }

    private void validateActiveMinutes(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "activeMinutes", errors);
        requireField(payload, "originPackage", errors);
        
        validateNonNegativeNumber(payload.get("activeMinutes"), "activeMinutes", errors);
    }

    private void validateWorkoutSession(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "source", errors);
        requireField(payload, "sessionId", errors);
        requireField(payload, "start", errors);
        requireField(payload, "end", errors);
    }

    private void validateSetPerformed(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "source", errors);
        requireField(payload, "sessionId", errors);
        requireField(payload, "exercise", errors);
        requireField(payload, "setIndex", errors);
        requireField(payload, "weightKg", errors);
        requireField(payload, "reps", errors);
        
        if (payload.containsKey("exercise") && payload.get("exercise") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> exercise = (Map<String, Object>) payload.get("exercise");
            requireField(exercise, "name", errors);
        }
        
        validatePositiveNumber(payload.get("setIndex"), "setIndex", errors);
        validateNonNegativeNumber(payload.get("weightKg"), "weightKg", errors);
        validateNonNegativeNumber(payload.get("reps"), "reps", errors);
    }

    private void validateMealLogged(Map<String, Object> payload, List<String> errors) {
        requireField(payload, "when", errors);
        requireField(payload, "items", errors);
        requireField(payload, "total", errors);
        
        if (payload.containsKey("items") && payload.get("items") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
            if (items.isEmpty()) {
                errors.add("items must contain at least one item");
            }
            for (Map<String, Object> item : items) {
                requireField(item, "name", errors);
                requireField(item, "kcal", errors);
            }
        }
        
        if (payload.containsKey("total") && payload.get("total") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> total = (Map<String, Object>) payload.get("total");
            requireField(total, "kcal", errors);
        }
    }

    private void requireField(Map<String, Object> map, String field, List<String> errors) {
        if (!map.containsKey(field) || map.get(field) == null) {
            errors.add("Missing required field: " + field);
        }
    }

    private void validateNonNegativeNumber(Object value, String field, List<String> errors) {
        if (value == null) return;
        
        try {
            double num = ((Number) value).doubleValue();
            if (num < 0) {
                errors.add(field + " must be non-negative");
            }
        } catch (Exception e) {
            errors.add(field + " must be a number");
        }
    }

    private void validatePositiveNumber(Object value, String field, List<String> errors) {
        if (value == null) return;
        
        try {
            double num = ((Number) value).doubleValue();
            if (num < 1) {
                errors.add(field + " must be positive (>= 1)");
            }
        } catch (Exception e) {
            errors.add(field + " must be a number");
        }
    }
}

