package com.healthassistant.application.workout.projection;

import com.healthassistant.domain.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkoutProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String WORKOUT_RECORDED_V1 = "WorkoutRecorded.v1";

    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutExerciseProjectionJpaRepository exerciseRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;

    @Transactional
    public void projectWorkout(Event event) {
        if (!WORKOUT_RECORDED_V1.equals(event.eventType().value())) {
            return;
        }

        Map<String, Object> payload = event.payload();
        String workoutId = getString(payload, "workoutId");

        if (workoutId == null) {
            log.warn("WorkoutRecorded event missing workoutId, skipping projection");
            return;
        }

        if (workoutRepository.existsByWorkoutId(workoutId)) {
            log.debug("Workout projection already exists for workoutId: {}, skipping", workoutId);
            return;
        }

        try {
            buildWorkoutProjection(event, payload, workoutId);
            log.info("Created workout projection for workoutId: {}", workoutId);
        } catch (Exception e) {
            log.error("Failed to create workout projection for workoutId: {}", workoutId, e);
            throw new WorkoutProjectionException("Failed to project workout: " + workoutId, e);
        }
    }

    private void buildWorkoutProjection(Event event, Map<String, Object> payload, String workoutId) {
        Instant performedAt = parseInstant(payload.get("performedAt"));
        if (performedAt == null) {
            performedAt = event.occurredAt();
        }

        LocalDate performedDate = performedAt.atZone(POLAND_ZONE).toLocalDate();
        String source = getString(payload, "source");
        String note = getString(payload, "note");

        WorkoutProjectionJpaEntity workout = WorkoutProjectionJpaEntity.builder()
                .workoutId(workoutId)
                .performedAt(performedAt)
                .performedDate(performedDate)
                .source(source)
                .note(note)
                .deviceId(event.deviceId().value())
                .eventId(event.eventId().value())
                .totalExercises(0)
                .totalSets(0)
                .totalVolumeKg(BigDecimal.ZERO)
                .totalWorkingVolumeKg(BigDecimal.ZERO)
                .exercises(new ArrayList<>())
                .build();

        if (!(payload.get("exercises") instanceof List<?> exercises)) {
            log.warn("WorkoutRecorded event missing exercises list, creating empty projection");
            return;
        }

        int totalSets = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalWorkingVolume = BigDecimal.ZERO;
        List<WorkoutSetProjectionJpaEntity> allSets = new ArrayList<>();

        for (Object exerciseObj : exercises) {
            if (!(exerciseObj instanceof Map<?, ?> exerciseMap)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> exercise = (Map<String, Object>) exerciseMap;

            ExerciseProjectionResult result = buildExerciseProjection(workout, exercise);
            WorkoutExerciseProjectionJpaEntity exerciseProjection = result.exerciseProjection;
            workout.addExercise(exerciseProjection);
            allSets.addAll(result.sets);

            totalSets += exerciseProjection.getTotalSets();
            totalVolume = totalVolume.add(exerciseProjection.getTotalVolumeKg());

            // Add working volume (non-warmup sets only)
            for (WorkoutSetProjectionJpaEntity set : result.sets) {
                if (!set.getIsWarmup()) {
                    totalWorkingVolume = totalWorkingVolume.add(set.getVolumeKg());
                }
            }
        }

        workout.setTotalExercises(workout.getExercises().size());
        workout.setTotalSets(totalSets);
        workout.setTotalVolumeKg(totalVolume);
        workout.setTotalWorkingVolumeKg(totalWorkingVolume);

        // Save workout first (cascades to exercises)
        workoutRepository.save(workout);

        // Save all sets
        if (!allSets.isEmpty()) {
            setRepository.saveAll(allSets);
        }

    }

    private ExerciseProjectionResult buildExerciseProjection(
            WorkoutProjectionJpaEntity workout,
            Map<String, Object> exercise
    ) {
        String exerciseName = getString(exercise, "name");
        String muscleGroup = getString(exercise, "muscleGroup");
        Integer orderInWorkout = getInteger(exercise, "orderInWorkout", 0);

        WorkoutExerciseProjectionJpaEntity exerciseProjection = WorkoutExerciseProjectionJpaEntity.builder()
                .workout(workout)
                .exerciseName(exerciseName)
                .muscleGroup(muscleGroup)
                .orderInWorkout(orderInWorkout)
                .totalSets(0)
                .totalVolumeKg(BigDecimal.ZERO)
                .maxWeightKg(BigDecimal.ZERO)
                .build();

        List<WorkoutSetProjectionJpaEntity> sets = new ArrayList<>();

        if (!(exercise.get("sets") instanceof List<?> setsList)) {
            return new ExerciseProjectionResult(exerciseProjection, sets);
        }

        int totalSets = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal maxWeight = BigDecimal.ZERO;

        for (Object setObj : setsList) {
            if (!(setObj instanceof Map<?, ?> setMap)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> set = (Map<String, Object>) setMap;

            WorkoutSetProjectionJpaEntity setProjection = buildSetProjection(workout.getWorkoutId(), exerciseName, set);
            sets.add(setProjection);

            totalSets++;
            totalVolume = totalVolume.add(setProjection.getVolumeKg());
            if (setProjection.getWeightKg().compareTo(maxWeight) > 0) {
                maxWeight = setProjection.getWeightKg();
            }
        }

        exerciseProjection.setTotalSets(totalSets);
        exerciseProjection.setTotalVolumeKg(totalVolume);
        exerciseProjection.setMaxWeightKg(maxWeight);

        return new ExerciseProjectionResult(exerciseProjection, sets);
    }

    private record ExerciseProjectionResult(
            WorkoutExerciseProjectionJpaEntity exerciseProjection,
            List<WorkoutSetProjectionJpaEntity> sets
    ) {}

    private WorkoutSetProjectionJpaEntity buildSetProjection(
            String workoutId,
            String exerciseName,
            Map<String, Object> set
    ) {
        Integer setNumber = getInteger(set, "setNumber", 0);
        Double weightKg = getDouble(set, "weightKg");
        Integer reps = getInteger(set, "reps", 0);
        Boolean isWarmup = getBoolean(set, "isWarmup", false);

        BigDecimal weight = weightKg != null ? BigDecimal.valueOf(weightKg) : BigDecimal.ZERO;
        BigDecimal volume = weight.multiply(BigDecimal.valueOf(reps));

        return WorkoutSetProjectionJpaEntity.builder()
                .workoutId(workoutId)
                .exerciseName(exerciseName)
                .setNumber(setNumber)
                .weightKg(weight)
                .reps(reps)
                .isWarmup(isWarmup)
                .volumeKg(volume)
                .build();
    }

    // Helper methods

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        try {
            return Boolean.parseBoolean(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                log.warn("Failed to parse Instant from string: {}", value);
                return null;
            }
        }
        return null;
    }

    public static class WorkoutProjectionException extends RuntimeException {
        public WorkoutProjectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
