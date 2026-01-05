package com.healthassistant.workoutimport;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutEventMapper {

    private static final String WORKOUT_SOURCE = "GYMRUN_SCREENSHOT";
    private static final String WORKOUT_EVENT_TYPE = "WorkoutRecorded.v1";

    private final ExerciseMatcher exerciseMatcher;

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
        ExtractedWorkoutData data, String workoutId, DeviceId deviceId
    ) {
        List<WorkoutPayload.Exercise> exercises = data.exercises().stream()
            .map(this::mapExerciseWithResolution)
            .toList();

        WorkoutPayload payload = new WorkoutPayload(
            workoutId,
            data.performedAt(),
            WORKOUT_SOURCE,
            data.note(),
            exercises
        );

        String idempotencyKeyValue = deviceId.value() + "|workout|" + workoutId;
        IdempotencyKey idempotencyKey = IdempotencyKey.of(idempotencyKeyValue);

        log.debug("Created workout event envelope: workoutId={}, exercises={}, idempotencyKey={}",
            workoutId, exercises.size(), idempotencyKeyValue);

        return new StoreHealthEventsCommand.EventEnvelope(
            idempotencyKey,
            WORKOUT_EVENT_TYPE,
            data.performedAt(),
            payload
        );
    }

    private WorkoutPayload.Exercise mapExerciseWithResolution(ExtractedWorkoutData.Exercise exercise) {
        String resolvedExerciseId = exerciseMatcher.resolveExerciseId(exercise);

        List<WorkoutPayload.ExerciseSet> sets = exercise.sets().stream()
            .map(this::mapSet)
            .toList();

        log.debug("Mapped exercise '{}' -> exerciseId: {}", exercise.name(), resolvedExerciseId);

        return new WorkoutPayload.Exercise(
            exercise.name(),
            resolvedExerciseId,
            exercise.muscleGroup(),
            exercise.orderInWorkout(),
            sets
        );
    }

    private WorkoutPayload.ExerciseSet mapSet(ExtractedWorkoutData.ExerciseSet set) {
        return new WorkoutPayload.ExerciseSet(
            set.setNumber(),
            set.weightKg(),
            set.reps(),
            set.isWarmup()
        );
    }
}
