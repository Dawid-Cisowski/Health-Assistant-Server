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
            case DistanceBucketRecorded distance -> validateDistanceBucket(payload, errors);
            case HeartRateSummaryRecorded hr -> validateHeartRateSummary(payload, errors);
            case SleepSessionRecorded sleep -> validateSleepSession(payload, errors);
            case ActiveCaloriesBurnedRecorded calories -> validateActiveCaloriesBurned(payload, errors);
            case ActiveMinutesRecorded minutes -> validateActiveMinutes(payload, errors);
            case WalkingSessionRecorded walking -> validateWalkingSession(payload, errors);
            case WorkoutRecorded workout -> validateWorkout(payload, errors);
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

    private void validateWorkout(Map<String, Object> payload, List<EventValidationError> errors) {
        requireFields(payload, errors, "workoutId", "performedAt", "source");

        // Validate exercises list exists and is not empty
        Object exercisesObj = payload.get("exercises");
        if (exercisesObj == null) {
            errors.add(EventValidationError.missingField("exercises"));
            return;
        }

        if (!(exercisesObj instanceof List)) {
            errors.add(EventValidationError.invalidValue("exercises", "must be a list"));
            return;
        }

        List<?> exercises = (List<?>) exercisesObj;
        if (exercises.isEmpty()) {
            errors.add(EventValidationError.invalidValue("exercises", "cannot be empty"));
            return;
        }

        // Validate each exercise
        for (int i = 0; i < exercises.size(); i++) {
            Object exerciseObj = exercises.get(i);
            if (!(exerciseObj instanceof Map)) {
                errors.add(EventValidationError.invalidValue("exercises[" + i + "]", "must be an object"));
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> exercise = (Map<String, Object>) exerciseObj;

            // Validate exercise fields
            if (!exercise.containsKey("name") || exercise.get("name") == null) {
                errors.add(EventValidationError.missingField("exercises[" + i + "].name"));
            }

            if (!exercise.containsKey("orderInWorkout") || exercise.get("orderInWorkout") == null) {
                errors.add(EventValidationError.missingField("exercises[" + i + "].orderInWorkout"));
            } else {
                validatePositiveNumber(exercise.get("orderInWorkout"), "exercises[" + i + "].orderInWorkout", errors);
            }

            // Validate sets
            Object setsObj = exercise.get("sets");
            if (setsObj == null) {
                errors.add(EventValidationError.missingField("exercises[" + i + "].sets"));
                continue;
            }

            if (!(setsObj instanceof List)) {
                errors.add(EventValidationError.invalidValue("exercises[" + i + "].sets", "must be a list"));
                continue;
            }

            List<?> sets = (List<?>) setsObj;
            if (sets.isEmpty()) {
                errors.add(EventValidationError.invalidValue("exercises[" + i + "].sets", "cannot be empty"));
                continue;
            }

            // Validate each set
            for (int j = 0; j < sets.size(); j++) {
                Object setObj = sets.get(j);
                if (!(setObj instanceof Map)) {
                    errors.add(EventValidationError.invalidValue("exercises[" + i + "].sets[" + j + "]", "must be an object"));
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> set = (Map<String, Object>) setObj;

                String setPrefix = "exercises[" + i + "].sets[" + j + "]";

                // Validate set fields
                if (!set.containsKey("setNumber") || set.get("setNumber") == null) {
                    errors.add(EventValidationError.missingField(setPrefix + ".setNumber"));
                } else {
                    validatePositiveNumber(set.get("setNumber"), setPrefix + ".setNumber", errors);
                }

                if (!set.containsKey("weightKg") || set.get("weightKg") == null) {
                    errors.add(EventValidationError.missingField(setPrefix + ".weightKg"));
                } else {
                    validateNonNegativeNumber(set.get("weightKg"), setPrefix + ".weightKg", errors);
                }

                if (!set.containsKey("reps") || set.get("reps") == null) {
                    errors.add(EventValidationError.missingField(setPrefix + ".reps"));
                } else {
                    validatePositiveNumber(set.get("reps"), setPrefix + ".reps", errors);
                }

                if (!set.containsKey("isWarmup") || set.get("isWarmup") == null) {
                    errors.add(EventValidationError.missingField(setPrefix + ".isWarmup"));
                } else if (!(set.get("isWarmup") instanceof Boolean)) {
                    errors.add(EventValidationError.invalidValue(setPrefix + ".isWarmup", "must be a boolean"));
                }
            }
        }
    }
}

