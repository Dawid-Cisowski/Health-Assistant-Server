package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.payload.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
class EventValidator {

    private final Validator validator;

    List<EventValidationError> validate(EventPayload payload) {
        List<EventValidationError> errors = new ArrayList<>();

        if (payload == null) {
            errors.add(EventValidationError.emptyPayload());
            return errors;
        }

        Set<ConstraintViolation<EventPayload>> violations = validator.validate(payload);
        for (ConstraintViolation<EventPayload> violation : violations) {
            String field = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.add(EventValidationError.invalidValue(field, message));
        }

        switch (payload) {
            case StepsPayload steps -> validateBucketTimes(steps.bucketStart(), steps.bucketEnd(), errors);
            case DistanceBucketPayload distance -> validateBucketTimes(distance.bucketStart(), distance.bucketEnd(), errors);
            case HeartRatePayload hr -> {
                validateBucketTimes(hr.bucketStart(), hr.bucketEnd(), errors);
                validateHeartRateMinMax(hr, errors);
            }
            case SleepSessionPayload sleep -> validateSleepTimes(sleep, errors);
            case ActiveCaloriesPayload calories -> validateBucketTimes(calories.bucketStart(), calories.bucketEnd(), errors);
            case ActiveMinutesPayload minutes -> validateBucketTimes(minutes.bucketStart(), minutes.bucketEnd(), errors);
            case WalkingSessionPayload walking -> validateSessionTimes(walking.start(), walking.end(), errors);
            case WorkoutPayload workout -> validateWorkoutStructure(workout, errors);
            case MealRecordedPayload meal -> { /* No cross-field validation needed */ }
        }

        return errors;
    }

    private void validateBucketTimes(Instant start, Instant end, List<EventValidationError> errors) {
        if (start != null && end != null && !start.isBefore(end)) {
            errors.add(EventValidationError.invalidValue("bucketEnd", "must be after bucketStart"));
        }
    }

    private void validateSessionTimes(Instant start, Instant end, List<EventValidationError> errors) {
        if (start != null && end != null && !start.isBefore(end)) {
            errors.add(EventValidationError.invalidValue("end", "must be after start"));
        }
    }

    private void validateSleepTimes(SleepSessionPayload sleep, List<EventValidationError> errors) {
        if (sleep.sleepStart() != null && sleep.sleepEnd() != null && !sleep.sleepStart().isBefore(sleep.sleepEnd())) {
            errors.add(EventValidationError.invalidValue("sleepEnd", "must be after sleepStart"));
        }
    }

    private void validateHeartRateMinMax(HeartRatePayload hr, List<EventValidationError> errors) {
        if (hr.min() != null && hr.max() != null && hr.min() > hr.max()) {
            errors.add(EventValidationError.invalidValue("min", "cannot be greater than max"));
        }
        if (hr.avg() != null && hr.min() != null && hr.avg() < hr.min()) {
            errors.add(EventValidationError.invalidValue("avg", "cannot be less than min"));
        }
        if (hr.avg() != null && hr.max() != null && hr.avg() > hr.max()) {
            errors.add(EventValidationError.invalidValue("avg", "cannot be greater than max"));
        }
    }

    private void validateWorkoutStructure(WorkoutPayload workout, List<EventValidationError> errors) {
        if (workout.exercises() == null || workout.exercises().isEmpty()) {
            errors.add(EventValidationError.invalidValue("exercises", "cannot be empty"));
            return;
        }

        IntStream.range(0, workout.exercises().size()).forEach(i -> {
            WorkoutPayload.Exercise exercise = workout.exercises().get(i);
            String prefix = "exercises[%d]".formatted(i);
            if (exercise.sets() == null || exercise.sets().isEmpty()) {
                errors.add(EventValidationError.invalidValue(prefix + ".sets", "cannot be empty"));
            }
        });
    }
}
