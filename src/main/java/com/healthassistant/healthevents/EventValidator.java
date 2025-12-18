package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.payload.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
class EventValidator {

    private final Validator validator;

    List<EventValidationError> validate(EventPayload payload) {
        if (payload == null) {
            return List.of(EventValidationError.emptyPayload());
        }

        List<EventValidationError> errors = new ArrayList<>(collectBeanValidationErrors(payload));
        errors.addAll(validateDomainRules(payload));

        return errors;
    }

    private List<EventValidationError> collectBeanValidationErrors(EventPayload payload) {
        return validator.validate(payload).stream()
                .map(this::toValidationError)
                .toList();
    }

    private EventValidationError toValidationError(ConstraintViolation<EventPayload> violation) {
        return EventValidationError.invalidValue(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        );
    }

    private List<EventValidationError> validateDomainRules(EventPayload payload) {
        return switch (payload) {
            case StepsPayload p -> validateTimeRange(p.bucketStart(), p.bucketEnd(), "bucketEnd", "bucketStart");
            case DistanceBucketPayload p -> validateTimeRange(p.bucketStart(), p.bucketEnd(), "bucketEnd", "bucketStart");
            case ActiveCaloriesPayload p -> validateTimeRange(p.bucketStart(), p.bucketEnd(), "bucketEnd", "bucketStart");
            case ActiveMinutesPayload p -> validateTimeRange(p.bucketStart(), p.bucketEnd(), "bucketEnd", "bucketStart");
            case HeartRatePayload p -> validateHeartRate(p);
            case SleepSessionPayload p -> validateTimeRange(p.sleepStart(), p.sleepEnd(), "sleepEnd", "sleepStart");
            case WalkingSessionPayload p -> validateTimeRange(p.start(), p.end(), "end", "start");
            case WorkoutPayload p -> validateWorkoutStructure(p);
            case MealRecordedPayload meal -> List.of();
        };
    }

    private List<EventValidationError> validateTimeRange(java.time.Instant start, java.time.Instant end, String endField, String startField) {
        return TimeRange.validate(start, end, endField, startField)
                .map(List::of)
                .orElse(List.of());
    }

    private List<EventValidationError> validateHeartRate(HeartRatePayload hr) {
        List<EventValidationError> errors = new ArrayList<>();
        errors.addAll(validateTimeRange(hr.bucketStart(), hr.bucketEnd(), "bucketEnd", "bucketStart"));
        errors.addAll(HeartRateStats.validate(hr.min(), hr.avg(), hr.max()));
        return errors;
    }

    private List<EventValidationError> validateWorkoutStructure(WorkoutPayload workout) {
        if (workout.exercises() == null || workout.exercises().isEmpty()) {
            return List.of(EventValidationError.invalidValue("exercises", "cannot be empty"));
        }

        return IntStream.range(0, workout.exercises().size())
                .filter(i -> hasEmptySets(workout.exercises().get(i)))
                .mapToObj(i -> EventValidationError.invalidValue("exercises[%d].sets".formatted(i), "cannot be empty"))
                .toList();
    }

    private boolean hasEmptySets(WorkoutPayload.Exercise exercise) {
        return exercise.sets() == null || exercise.sets().isEmpty();
    }
}
