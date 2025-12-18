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

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
        ExtractedWorkoutData data, String workoutId, DeviceId deviceId
    ) {
        List<WorkoutPayload.Exercise> exercises = data.exercises().stream()
            .map(this::mapExercise)
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

    private WorkoutPayload.Exercise mapExercise(ExtractedWorkoutData.Exercise exercise) {
        List<WorkoutPayload.ExerciseSet> sets = exercise.sets().stream()
            .map(this::mapSet)
            .toList();

        return new WorkoutPayload.Exercise(
            exercise.name(),
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
