package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
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

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutExerciseProjectionJpaRepository exerciseRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;

    @Transactional
    public void projectWorkout(StoredEventData eventData) {
        if (!(eventData.payload() instanceof WorkoutPayload workout)) {
            log.warn("Expected WorkoutPayload but got {}, skipping projection",
                    eventData.payload().getClass().getSimpleName());
            return;
        }

        String workoutId = workout.workoutId();

        if (workoutId == null) {
            log.warn("WorkoutRecorded event missing workoutId, skipping projection");
            return;
        }

        if (workoutRepository.existsByWorkoutId(workoutId)) {
            log.debug("Workout projection already exists for workoutId: {}, skipping", workoutId);
            return;
        }

        try {
            buildWorkoutProjection(eventData, workout);
            log.info("Created workout projection for workoutId: {}", workoutId);
        } catch (Exception e) {
            log.error("Failed to create workout projection for workoutId: {}", workoutId, e);
            throw new WorkoutProjectionException("Failed to project workout: " + workoutId, e);
        }
    }

    private void buildWorkoutProjection(StoredEventData eventData, WorkoutPayload workout) {
        Instant performedAt = workout.performedAt();
        if (performedAt == null) {
            performedAt = eventData.occurredAt();
        }

        LocalDate performedDate = performedAt.atZone(POLAND_ZONE).toLocalDate();

        WorkoutProjectionJpaEntity workoutEntity = WorkoutProjectionJpaEntity.builder()
                .workoutId(workout.workoutId())
                .performedAt(performedAt)
                .performedDate(performedDate)
                .source(workout.source())
                .note(workout.note())
                .deviceId(eventData.deviceId().value())
                .eventId(eventData.eventId().value())
                .totalExercises(0)
                .totalSets(0)
                .totalVolumeKg(BigDecimal.ZERO)
                .totalWorkingVolumeKg(BigDecimal.ZERO)
                .exercises(new ArrayList<>())
                .build();

        List<WorkoutPayload.Exercise> exercises = workout.exercises();
        if (exercises == null || exercises.isEmpty()) {
            log.warn("WorkoutRecorded event missing exercises list, creating empty projection");
            workoutRepository.save(workoutEntity);
            return;
        }

        int totalSets = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalWorkingVolume = BigDecimal.ZERO;
        List<WorkoutSetProjectionJpaEntity> allSets = new ArrayList<>();

        for (WorkoutPayload.Exercise exercise : exercises) {
            ExerciseProjectionResult result = buildExerciseProjection(workoutEntity, exercise);
            WorkoutExerciseProjectionJpaEntity exerciseProjection = result.exerciseProjection;
            workoutEntity.addExercise(exerciseProjection);
            allSets.addAll(result.sets);

            totalSets += exerciseProjection.getTotalSets();
            totalVolume = totalVolume.add(exerciseProjection.getTotalVolumeKg());

            for (WorkoutSetProjectionJpaEntity set : result.sets) {
                if (!set.getIsWarmup()) {
                    totalWorkingVolume = totalWorkingVolume.add(set.getVolumeKg());
                }
            }
        }

        workoutEntity.setTotalExercises(workoutEntity.getExercises().size());
        workoutEntity.setTotalSets(totalSets);
        workoutEntity.setTotalVolumeKg(totalVolume);
        workoutEntity.setTotalWorkingVolumeKg(totalWorkingVolume);

        workoutRepository.save(workoutEntity);

        if (!allSets.isEmpty()) {
            setRepository.saveAll(allSets);
        }
    }

    private ExerciseProjectionResult buildExerciseProjection(
            WorkoutProjectionJpaEntity workoutEntity,
            WorkoutPayload.Exercise exercise
    ) {
        WorkoutExerciseProjectionJpaEntity exerciseProjection = WorkoutExerciseProjectionJpaEntity.builder()
                .workout(workoutEntity)
                .exerciseName(exercise.name())
                .muscleGroup(exercise.muscleGroup())
                .orderInWorkout(exercise.orderInWorkout())
                .totalSets(0)
                .totalVolumeKg(BigDecimal.ZERO)
                .maxWeightKg(BigDecimal.ZERO)
                .build();

        List<WorkoutSetProjectionJpaEntity> sets = new ArrayList<>();

        List<WorkoutPayload.ExerciseSet> exerciseSets = exercise.sets();
        if (exerciseSets == null || exerciseSets.isEmpty()) {
            return new ExerciseProjectionResult(exerciseProjection, sets);
        }

        int totalSets = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal maxWeight = BigDecimal.ZERO;

        for (WorkoutPayload.ExerciseSet set : exerciseSets) {
            WorkoutSetProjectionJpaEntity setProjection = buildSetProjection(
                    workoutEntity.getWorkoutId(),
                    exercise.name(),
                    set
            );
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
            WorkoutPayload.ExerciseSet set
    ) {
        BigDecimal weight = BigDecimal.valueOf(set.weightKg());
        BigDecimal volume = weight.multiply(BigDecimal.valueOf(set.reps()));

        return WorkoutSetProjectionJpaEntity.builder()
                .workoutId(workoutId)
                .exerciseName(exerciseName)
                .setNumber(set.setNumber())
                .weightKg(weight)
                .reps(set.reps())
                .isWarmup(set.isWarmup())
                .volumeKg(volume)
                .build();
    }

    public static class WorkoutProjectionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public WorkoutProjectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
