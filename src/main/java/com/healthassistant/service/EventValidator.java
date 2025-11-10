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
        "ActiveMinutesRecorded.v1"
        // WorkoutSessionImported.v1, SetPerformedImported.v1, MealLoggedEstimated.v1 - TODO: implement later
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

