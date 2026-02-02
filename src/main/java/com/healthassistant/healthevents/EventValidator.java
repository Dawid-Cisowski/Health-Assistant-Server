package com.healthassistant.healthevents;

import tools.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.dto.payload.*;
import com.healthassistant.healthevents.api.model.EventType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
class EventValidator {

    private final Validator validator;
    private final ObjectMapper objectMapper;

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
            case RestingHeartRatePayload p -> validateRestingHeartRate(p);
            case SleepSessionPayload p -> validateTimeRange(p.sleepStart(), p.sleepEnd(), "sleepEnd", "sleepStart");
            case WalkingSessionPayload p -> validateTimeRange(p.start(), p.end(), "end", "start");
            case WorkoutPayload p -> validateWorkoutStructure(p);
            case MealRecordedPayload meal -> List.of();
            case WeightMeasurementPayload weight -> validateWeightMeasurement(weight);
            case BodyMeasurementPayload body -> validateBodyMeasurement(body);
            case EventDeletedPayload p -> validateEventDeleted(p);
            case EventCorrectedPayload p -> validateEventCorrected(p);
        };
    }

    private List<EventValidationError> validateWeightMeasurement(WeightMeasurementPayload payload) {
        List<EventValidationError> errors = new ArrayList<>();

        if (payload.measuredAt() != null && payload.measuredAt().isAfter(java.time.Instant.now().plusSeconds(300))) {
            errors.add(EventValidationError.invalidValue("measuredAt", "cannot be more than 5 minutes in the future"));
        }

        return errors;
    }

    private List<EventValidationError> validateBodyMeasurement(BodyMeasurementPayload payload) {
        List<EventValidationError> errors = new ArrayList<>();

        if (payload.measuredAt() != null && payload.measuredAt().isAfter(java.time.Instant.now().plusSeconds(300))) {
            errors.add(EventValidationError.invalidValue("measuredAt", "cannot be more than 5 minutes in the future"));
        }

        // Validate notes field for dangerous content
        if (payload.notes() != null) {
            errors.addAll(validateNotesField(payload.notes()));
        }

        // At least one measurement must be provided
        if (hasNoMeasurements(payload)) {
            errors.add(EventValidationError.invalidValue("payload", "at least one body measurement must be provided"));
        }

        return errors;
    }

    private List<EventValidationError> validateNotesField(String notes) {
        List<EventValidationError> errors = new ArrayList<>();

        // Check for control characters (except space, tab is not allowed in notes)
        if (notes.matches(".*[\\x00-\\x1F].*")) {
            errors.add(EventValidationError.invalidValue("notes", "contains invalid control characters"));
        }

        return errors;
    }

    private boolean hasNoMeasurements(BodyMeasurementPayload payload) {
        return java.util.stream.Stream.of(
                payload.bicepsLeftCm(),
                payload.bicepsRightCm(),
                payload.forearmLeftCm(),
                payload.forearmRightCm(),
                payload.chestCm(),
                payload.waistCm(),
                payload.abdomenCm(),
                payload.hipsCm(),
                payload.neckCm(),
                payload.shouldersCm(),
                payload.thighLeftCm(),
                payload.thighRightCm(),
                payload.calfLeftCm(),
                payload.calfRightCm()
        ).allMatch(java.util.Objects::isNull);
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private List<EventValidationError> validateEventDeleted(EventDeletedPayload payload) {
        // Bean validation handles @NotBlank on targetEventId
        return List.of();
    }

    private List<EventValidationError> validateEventCorrected(EventCorrectedPayload payload) {
        List<EventValidationError> errors = new ArrayList<>();

        if (payload.correctedEventType() == null || payload.correctedEventType().isBlank()) {
            errors.add(EventValidationError.missingField("correctedEventType"));
            return errors;
        }

        EventType correctedType;
        try {
            correctedType = EventType.from(payload.correctedEventType());
        } catch (IllegalArgumentException e) {
            errors.add(EventValidationError.invalidValue("correctedEventType", "invalid event type: " + payload.correctedEventType()));
            return errors;
        }

        if (payload.correctedPayload() == null || payload.correctedPayload().isEmpty()) {
            errors.add(EventValidationError.missingField("correctedPayload"));
            return errors;
        }

        errors.addAll(validateCorrectedPayloadContent(correctedType, payload));

        return errors;
    }

    private List<EventValidationError> validateCorrectedPayloadContent(EventType correctedType, EventCorrectedPayload payload) {
        try {
            Class<? extends EventPayload> payloadClass = EventPayload.payloadClassFor(correctedType);
            EventPayload correctedPayload = objectMapper.convertValue(payload.correctedPayload(), payloadClass);
            return validate(correctedPayload).stream()
                    .map(error -> EventValidationError.invalidValue(
                            "correctedPayload." + error.field(),
                            error.message()))
                    .toList();
        } catch (IllegalArgumentException e) {
            log.debug("Failed to deserialize corrected payload for type {}: {}", correctedType.value(), e.getMessage());
            return List.of(EventValidationError.invalidValue("correctedPayload",
                    "Cannot deserialize payload for type " + correctedType.value() + ": " + e.getMessage()));
        }
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

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private List<EventValidationError> validateRestingHeartRate(RestingHeartRatePayload payload) {
        List<EventValidationError> errors = new ArrayList<>();

        if (payload.measuredAt() != null && payload.measuredAt().isAfter(java.time.Instant.now().plusSeconds(300))) {
            errors.add(EventValidationError.invalidValue("measuredAt", "cannot be more than 5 minutes in the future"));
        }

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
