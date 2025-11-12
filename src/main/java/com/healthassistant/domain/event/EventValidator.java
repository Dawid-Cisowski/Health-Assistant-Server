package com.healthassistant.domain.event;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EventValidator {

    public List<EventValidationError> validate(
            String eventTypeStr,
            Map<String, Object> payload
    ) {
        List<EventValidationError> errors = new ArrayList<>();

        EventType eventType;
        try {
            eventType = EventType.from(eventTypeStr);
        } catch (IllegalArgumentException e) {
            errors.add(EventValidationError.invalidEventType(eventTypeStr));
            return errors;
        }

        if (payload == null || payload.isEmpty()) {
            errors.add(EventValidationError.emptyPayload());
            return errors;
        }

        validatePayload(eventType, payload, errors);

        return errors;
    }

    private void validatePayload(EventType eventType, Map<String, Object> payload, List<EventValidationError> errors) {
        switch (eventType) {
            case StepsBucketedRecorded steps -> validateStepsBucketed(payload, errors);
            case HeartRateSummaryRecorded hr -> validateHeartRateSummary(payload, errors);
            case SleepSessionRecorded sleep -> validateSleepSession(payload, errors);
            case ActiveCaloriesBurnedRecorded calories -> validateActiveCaloriesBurned(payload, errors);
            case ActiveMinutesRecorded minutes -> validateActiveMinutes(payload, errors);
            case ExerciseSessionRecorded exercise -> validateExerciseSession(payload, errors);
        }
    }

    private void validateStepsBucketed(Map<String, Object> payload, List<EventValidationError> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "count", errors);
        requireField(payload, "originPackage", errors);

        if (payload.containsKey("count")) {
            validateNonNegativeNumber(payload.get("count"), "count", errors);
        }
    }

    private void validateHeartRateSummary(Map<String, Object> payload, List<EventValidationError> errors) {
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

    private void validateSleepSession(Map<String, Object> payload, List<EventValidationError> errors) {
        requireField(payload, "sleepStart", errors);
        requireField(payload, "sleepEnd", errors);
        requireField(payload, "totalMinutes", errors);
        requireField(payload, "originPackage", errors);

        validateNonNegativeNumber(payload.get("totalMinutes"), "totalMinutes", errors);
    }

    private void validateActiveCaloriesBurned(Map<String, Object> payload, List<EventValidationError> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "energyKcal", errors);
        requireField(payload, "originPackage", errors);

        validateNonNegativeNumber(payload.get("energyKcal"), "energyKcal", errors);
    }

    private void validateActiveMinutes(Map<String, Object> payload, List<EventValidationError> errors) {
        requireField(payload, "bucketStart", errors);
        requireField(payload, "bucketEnd", errors);
        requireField(payload, "activeMinutes", errors);
        requireField(payload, "originPackage", errors);

        validateNonNegativeNumber(payload.get("activeMinutes"), "activeMinutes", errors);
    }

    private void validateExerciseSession(Map<String, Object> payload, List<EventValidationError> errors) {
        System.out.println("=== ExerciseSessionRecorded.v1 VALIDATION (logging only) ===");
        System.out.println("Payload in validator: " + payload);
        System.out.println("============================================================");
        
        requireField(payload, "sessionId", errors);
        requireField(payload, "type", errors);
        requireField(payload, "start", errors);
        requireField(payload, "end", errors);
        requireField(payload, "durationMinutes", errors);
        requireField(payload, "originPackage", errors);

        if (payload.containsKey("durationMinutes")) {
            validateNonNegativeNumber(payload.get("durationMinutes"), "durationMinutes", errors);
        }
        if (payload.containsKey("steps")) {
            validateNonNegativeNumber(payload.get("steps"), "steps", errors);
        }
        if (payload.containsKey("avgHr")) {
            validateNonNegativeNumber(payload.get("avgHr"), "avgHr", errors);
        }
        if (payload.containsKey("maxHr")) {
            validateNonNegativeNumber(payload.get("maxHr"), "maxHr", errors);
        }
    }

    private void requireField(Map<String, Object> map, String field, List<EventValidationError> errors) {
        if (!map.containsKey(field) || map.get(field) == null) {
            errors.add(EventValidationError.missingField(field));
        }
    }

    private void validateNonNegativeNumber(Object value, String field, List<EventValidationError> errors) {
        if (value == null) return;

        try {
            double num = ((Number) value).doubleValue();
            if (num < 0) {
                errors.add(EventValidationError.invalidValue(field, "must be non-negative"));
            }
        } catch (Exception e) {
            errors.add(EventValidationError.invalidValue(field, "must be a number"));
        }
    }

    private void validatePositiveNumber(Object value, String field, List<EventValidationError> errors) {
        if (value == null) return;

        try {
            double num = ((Number) value).doubleValue();
            if (num < 1) {
                errors.add(EventValidationError.invalidValue(field, "must be positive (>= 1)"));
            }
        } catch (Exception e) {
            errors.add(EventValidationError.invalidValue(field, "must be a number"));
        }
    }
}

