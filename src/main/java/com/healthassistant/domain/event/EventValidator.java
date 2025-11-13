package com.healthassistant.domain.event;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
        requireFields(payload, errors, "bucketStart", "bucketEnd", "count", "originPackage");
        validateOptionalNonNegative(payload, errors, "count");
    }

    private void validateHeartRateSummary(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "bucketStart", "bucketEnd", "avg", "min", "max", "samples", "originPackage");
        validateNonNegative(payload, errors, "avg", "min", "max");
        validatePositiveNumber(payload.get("samples"), "samples", errors);
    }

    private void validateSleepSession(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "sleepStart", "sleepEnd", "totalMinutes", "originPackage");
        validateNonNegative(payload, errors, "totalMinutes");
    }

    private void validateActiveCaloriesBurned(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "bucketStart", "bucketEnd", "energyKcal", "originPackage");
        validateNonNegative(payload, errors, "energyKcal");
    }

    private void validateActiveMinutes(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "bucketStart", "bucketEnd", "activeMinutes", "originPackage");
        validateNonNegative(payload, errors, "activeMinutes");
    }

    private void validateExerciseSession(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "sessionId", "type", "start", "end", "durationMinutes", "originPackage");
        validateOptionalNonNegative(payload, errors, "durationMinutes", "steps", "avgHr", "maxHr");
    }

    private void requireFields(Map<String, Object> payload, List<EventValidationError> errors, String... fields) {
        Arrays.stream(fields).forEach(field -> {
            if (!payload.containsKey(field) || payload.get(field) == null) {
                errors.add(EventValidationError.missingField(field));
            }
        });
    }

    private void validateNonNegative(Map<String, Object> payload, List<EventValidationError> errors, String... fields) {
        Arrays.stream(fields).forEach(field ->
            validateNonNegativeNumber(payload.get(field), field, errors)
        );
    }

    private void validateOptionalNonNegative(Map<String, Object> payload, List<EventValidationError> errors, String... fields) {
        Arrays.stream(fields)
            .filter(payload::containsKey)
            .forEach(field -> validateNonNegativeNumber(payload.get(field), field, errors));
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

