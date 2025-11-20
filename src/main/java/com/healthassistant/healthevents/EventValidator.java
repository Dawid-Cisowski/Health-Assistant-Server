package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.EventType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
class EventValidator {

    List<EventValidationError> validate(String eventTypeStr, Map<String, Object> payload) {
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
        String typeValue = eventType.value();
        switch (typeValue) {
            case "StepsBucketedRecorded.v1" -> validateStepsBucketed(payload, errors);
            case "DistanceBucketRecorded.v1" -> validateDistanceBucket(payload, errors);
            case "HeartRateSummaryRecorded.v1" -> validateHeartRateSummary(payload, errors);
            case "SleepSessionRecorded.v1" -> validateSleepSession(payload, errors);
            case "ActiveCaloriesBurnedRecorded.v1" -> validateActiveCaloriesBurned(payload, errors);
            case "ActiveMinutesRecorded.v1" -> validateActiveMinutes(payload, errors);
            case "WalkingSessionRecorded.v1" -> validateWalkingSession(payload, errors);
            case "WorkoutRecorded.v1" -> validateWorkout(payload, errors);
        }
    }

    private void validateStepsBucketed(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "bucketStart", "bucketEnd", "count", "originPackage");
        validateOptionalNonNegative(payload, errors, "count");
    }

    private void validateDistanceBucket(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "bucketStart", "bucketEnd", "distanceMeters", "originPackage");
        validateOptionalNonNegative(payload, errors, "distanceMeters");
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

    private void validateWalkingSession(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "sessionId", "start", "end", "durationMinutes", "originPackage");
        validateOptionalNonNegative(payload, errors, "durationMinutes", "totalSteps", "totalDistanceMeters", "totalCalories", "avgHeartRate", "maxHeartRate");
    }

    private void validateWorkout(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "workoutId", "performedAt", "source");

        if (!(payload.get("exercises") instanceof List<?> exercises)) {
            errors.add(payload.containsKey("exercises")
                    ? EventValidationError.invalidValue("exercises", "must be a list")
                    : EventValidationError.missingField("exercises"));
            return;
        }

        if (exercises.isEmpty()) {
            errors.add(EventValidationError.invalidValue("exercises", "cannot be empty"));
            return;
        }

        IntStream.range(0, exercises.size())
                .forEach(i -> validateExercise(exercises.get(i), i, errors));
    }

    private void validateExercise(Object exerciseObj, int index, List<EventValidationError> errors) {
        if (!(exerciseObj instanceof Map<?, ?> exercise)) {
            errors.add(EventValidationError.invalidValue("exercises[%d]".formatted(index), "must be an object"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> exerciseMap = (Map<String, Object>) exercise;

        String prefix = "exercises[%d]".formatted(index);

        validateRequiredField(exerciseMap, "name", prefix, errors);
        validateRequiredPositive(exerciseMap, "orderInWorkout", prefix, errors);

        if (!(exerciseMap.get("sets") instanceof List<?> sets)) {
            errors.add(exerciseMap.containsKey("sets")
                    ? EventValidationError.invalidValue(prefix + ".sets", "must be a list")
                    : EventValidationError.missingField(prefix + ".sets"));
            return;
        }

        if (sets.isEmpty()) {
            errors.add(EventValidationError.invalidValue(prefix + ".sets", "cannot be empty"));
            return;
        }

        IntStream.range(0, sets.size())
                .forEach(j -> validateSet(sets.get(j), index, j, errors));
    }

    private void validateSet(Object setObj, int exerciseIndex, int setIndex, List<EventValidationError> errors) {
        if (!(setObj instanceof Map<?, ?> set)) {
            errors.add(EventValidationError.invalidValue(
                    "exercises[%d].sets[%d]".formatted(exerciseIndex, setIndex),
                    "must be an object"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> setMap = (Map<String, Object>) set;

        String prefix = "exercises[%d].sets[%d]".formatted(exerciseIndex, setIndex);

        validateRequiredPositive(setMap, "setNumber", prefix, errors);
        validateRequiredNonNegative(setMap, "weightKg", prefix, errors);
        validateRequiredPositive(setMap, "reps", prefix, errors);
        validateRequiredBoolean(setMap, "isWarmup", prefix, errors);
    }

    private void validateRequiredField(Map<String, Object> map, String field, String prefix, List<EventValidationError> errors) {
        if (!map.containsKey(field) || map.get(field) == null) {
            errors.add(EventValidationError.missingField(prefix + "." + field));
        }
    }

    private void validateRequiredPositive(Map<String, Object> map, String field, String prefix, List<EventValidationError> errors) {
        if (!map.containsKey(field) || map.get(field) == null) {
            errors.add(EventValidationError.missingField(prefix + "." + field));
        } else {
            validatePositiveNumber(map.get(field), prefix + "." + field, errors);
        }
    }

    private void validateRequiredNonNegative(Map<String, Object> map, String field, String prefix, List<EventValidationError> errors) {
        if (!map.containsKey(field) || map.get(field) == null) {
            errors.add(EventValidationError.missingField(prefix + "." + field));
        } else {
            validateNonNegativeNumber(map.get(field), prefix + "." + field, errors);
        }
    }

    private void validateRequiredBoolean(Map<String, Object> map, String field, String prefix, List<EventValidationError> errors) {
        if (!map.containsKey(field) || map.get(field) == null) {
            errors.add(EventValidationError.missingField(prefix + "." + field));
        } else if (!(map.get(field) instanceof Boolean)) {
            errors.add(EventValidationError.invalidValue(prefix + "." + field, "must be a boolean"));
        }
    }

    private void requireFields(Map<String, Object> payload, List<EventValidationError> errors, String... fields) {
        Arrays.stream(fields)
                .filter(field -> !payload.containsKey(field) || payload.get(field) == null)
                .forEach(field -> errors.add(EventValidationError.missingField(field)));
    }

    private void validateNonNegative(Map<String, Object> payload, List<EventValidationError> errors, String... fields) {
        Arrays.stream(fields)
                .forEach(field -> validateNonNegativeNumber(payload.get(field), field, errors));
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
