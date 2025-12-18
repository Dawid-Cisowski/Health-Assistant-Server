package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
}
