package com.healthassistant.workoutimport;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.workoutimport.dto.ExtractedWorkoutData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutEventMapper {

    private static final String WORKOUT_SOURCE = "GYMRUN_SCREENSHOT";
    private static final String WORKOUT_EVENT_TYPE = "WorkoutRecorded.v1";

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
        ExtractedWorkoutData data, String workoutId, DeviceId deviceId
    ) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("workoutId", workoutId);
        payload.put("performedAt", data.performedAt().toString());
        payload.put("source", WORKOUT_SOURCE);

        if (data.note() != null) {
            payload.put("note", data.note());
        }

        List<Map<String, Object>> exercises = data.exercises().stream()
            .map(this::mapExercise)
            .toList();
        payload.put("exercises", exercises);

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

    private Map<String, Object> mapExercise(ExtractedWorkoutData.Exercise exercise) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", exercise.name());
        map.put("orderInWorkout", exercise.orderInWorkout());

        if (exercise.muscleGroup() != null) {
            map.put("muscleGroup", exercise.muscleGroup());
        }

        List<Map<String, Object>> sets = exercise.sets().stream()
            .map(this::mapSet)
            .toList();
        map.put("sets", sets);

        return map;
    }

    private Map<String, Object> mapSet(ExtractedWorkoutData.ExerciseSet set) {
        Map<String, Object> map = new HashMap<>();
        map.put("setNumber", set.setNumber());
        map.put("weightKg", set.weightKg());
        map.put("reps", set.reps());
        map.put("isWarmup", set.isWarmup());
        return map;
    }
}
