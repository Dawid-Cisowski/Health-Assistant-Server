package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class WorkoutFactory {

    Optional<Workout> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof WorkoutPayload payload)) {
            return Optional.empty();
        }

        if (payload.workoutId() == null) {
            return Optional.empty();
        }

        Instant performedAt = payload.performedAt() != null ? payload.performedAt() : eventData.occurredAt();

        List<Exercise> exercises = payload.exercises() != null
                ? payload.exercises().stream().map(this::toExercise).toList()
                : List.of();

        return Optional.of(Workout.create(
                payload.workoutId(),
                performedAt,
                payload.source(),
                payload.note(),
                eventData.deviceId().value(),
                eventData.eventId().value(),
                exercises
        ));
    }

    @SuppressWarnings("unchecked")
    Optional<Workout> createFromCorrectionPayload(String deviceId, Map<String, Object> payload, Instant occurredAt) {
        String workoutId = payload.get("workoutId") != null ? payload.get("workoutId").toString() : null;
        if (workoutId == null) {
            return Optional.empty();
        }

        Instant performedAt = parseInstant(payload.get("performedAt"));
        if (performedAt == null) performedAt = occurredAt;

        String source = payload.get("source") != null ? payload.get("source").toString() : "correction";
        String note = payload.get("note") != null ? payload.get("note").toString() : null;
        String correctionEventId = "corrected-" + UUID.randomUUID().toString().substring(0, 8);

        List<Exercise> exercises = List.of();
        Object exercisesObj = payload.get("exercises");
        if (exercisesObj instanceof List<?> exerciseList) {
            exercises = exerciseList.stream()
                    .filter(e -> e instanceof Map)
                    .map(e -> toExerciseFromMap((Map<String, Object>) e))
                    .toList();
        }

        return Optional.of(Workout.create(
                workoutId,
                performedAt,
                source,
                note,
                deviceId,
                correctionEventId,
                exercises
        ));
    }

    @SuppressWarnings("unchecked")
    private Exercise toExerciseFromMap(Map<String, Object> map) {
        String name = map.get("name") != null ? map.get("name").toString() : "Unknown";
        String muscleGroup = map.get("muscleGroup") != null ? map.get("muscleGroup").toString() : null;
        Integer order = parseInteger(map.get("orderInWorkout"));

        List<ExerciseSet> sets = List.of();
        Object setsObj = map.get("sets");
        if (setsObj instanceof List<?> setList) {
            sets = setList.stream()
                    .filter(s -> s instanceof Map)
                    .map(s -> toSetFromMap((Map<String, Object>) s))
                    .toList();
        }

        return new Exercise(name, muscleGroup, order, sets);
    }

    private ExerciseSet toSetFromMap(Map<String, Object> map) {
        Integer setNumber = parseInteger(map.get("setNumber"));
        Double weightKg = parseDouble(map.get("weightKg"));
        Integer reps = parseInteger(map.get("reps"));
        boolean isWarmup = map.get("isWarmup") instanceof Boolean b ? b : false;

        return ExerciseSet.of(setNumber, weightKg, reps, isWarmup);
    }

    private Exercise toExercise(WorkoutPayload.Exercise payload) {
        List<ExerciseSet> sets = payload.sets() != null
                ? payload.sets().stream().map(this::toSet).toList()
                : List.of();

        return new Exercise(
                payload.name(),
                payload.muscleGroup(),
                payload.orderInWorkout(),
                sets
        );
    }

    private ExerciseSet toSet(WorkoutPayload.ExerciseSet payload) {
        return ExerciseSet.of(
                payload.setNumber(),
                payload.weightKg(),
                payload.reps(),
                payload.isWarmup()
        );
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        return Instant.parse(value.toString());
    }

    private Integer parseInteger(Object value) {
        return switch (value) {
            case null -> null;
            case Integer i -> i;
            case Number n -> n.intValue();
            default -> Integer.parseInt(value.toString());
        };
    }

    private Double parseDouble(Object value) {
        return switch (value) {
            case null -> null;
            case Double d -> d;
            case Number n -> n.doubleValue();
            default -> Double.parseDouble(value.toString());
        };
    }
}
