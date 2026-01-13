package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class WorkoutService implements WorkoutFacade {

    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutExerciseProjectionJpaRepository exerciseRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;
    private final WorkoutProjector workoutProjector;
    private final ExerciseCatalog exerciseCatalog;
    private final ExerciseDefinitionRepository exerciseDefinitionRepository;
    private final ExerciseStatisticsService exerciseStatisticsService;

    @Override
    public Optional<WorkoutDetailResponse> getWorkoutDetails(String deviceId, String workoutId) {
        Optional<WorkoutProjectionJpaEntity> workoutOpt = workoutRepository.findByDeviceIdAndWorkoutId(deviceId, workoutId);

        if (workoutOpt.isEmpty()) {
            return Optional.empty();
        }

        WorkoutProjectionJpaEntity workout = workoutOpt.get();
        List<WorkoutExerciseProjectionJpaEntity> exercises = workout.getExercises();
        List<WorkoutSetProjectionJpaEntity> sets = setRepository.findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(workoutId);

        var setsByExercise = sets.stream()
                .collect(Collectors.groupingBy(WorkoutSetProjectionJpaEntity::getExerciseName));

        List<WorkoutDetailResponse.ExerciseDetail> exerciseDetails = exercises.stream()
                .sorted(Comparator.comparing(WorkoutExerciseProjectionJpaEntity::getOrderInWorkout))
                .map(exercise -> {
                    List<WorkoutSetProjectionJpaEntity> exerciseSets = setsByExercise.getOrDefault(
                            exercise.getExerciseName(),
                            List.of()
                    );

                    List<WorkoutDetailResponse.SetDetail> setDetails = exerciseSets.stream()
                            .sorted(Comparator.comparing(WorkoutSetProjectionJpaEntity::getSetNumber))
                            .map(set -> new WorkoutDetailResponse.SetDetail(
                                    set.getSetNumber(), set.getWeightKg(), set.getReps(),
                                    set.getIsWarmup(), set.getVolumeKg()))
                            .toList();

                    return new WorkoutDetailResponse.ExerciseDetail(
                            exercise.getExerciseId(), exercise.getExerciseName(),
                            exercise.getMuscleGroup(), exercise.getOrderInWorkout(),
                            exercise.getTotalSets(), exercise.getTotalVolumeKg(),
                            exercise.getMaxWeightKg(), setDetails);
                })
                .toList();

        return Optional.of(new WorkoutDetailResponse(
                workout.getWorkoutId(), workout.getPerformedAt(), workout.getPerformedDate(),
                workout.getSource(), workout.getNote(), workout.getTotalExercises(),
                workout.getTotalSets(), workout.getTotalVolumeKg(), workout.getTotalWorkingVolumeKg(),
                exerciseDetails));
    }

    @Override
    public List<WorkoutDetailResponse> getWorkoutsByDateRange(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<WorkoutProjectionJpaEntity> workouts = workoutRepository.findByDeviceIdAndPerformedDateBetweenOrderByPerformedAtDesc(
                deviceId,
                startDate,
                endDate
        );

        return workouts.stream()
                .map(workout -> {
                    List<WorkoutDetailResponse.ExerciseDetail> exerciseDetails = workout.getExercises().stream()
                            .sorted(Comparator.comparing(WorkoutExerciseProjectionJpaEntity::getOrderInWorkout))
                            .map(exercise -> new WorkoutDetailResponse.ExerciseDetail(
                                    exercise.getExerciseId(), exercise.getExerciseName(),
                                    exercise.getMuscleGroup(), exercise.getOrderInWorkout(),
                                    exercise.getTotalSets(), exercise.getTotalVolumeKg(),
                                    exercise.getMaxWeightKg(), List.of()))
                            .toList();

                    return new WorkoutDetailResponse(
                            workout.getWorkoutId(), workout.getPerformedAt(), workout.getPerformedDate(),
                            workout.getSource(), workout.getNote(), workout.getTotalExercises(),
                            workout.getTotalSets(), workout.getTotalVolumeKg(), workout.getTotalWorkingVolumeKg(),
                            exerciseDetails);
                })
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all workout projections");
        setRepository.deleteAll();
        exerciseRepository.deleteAll();
        workoutRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteProjectionsByDeviceId(String deviceId) {
        log.warn("Deleting workout projections for deviceId: {}", deviceId);
        workoutRepository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting workout projections for device {} date {}", deviceId, date);
        workoutRepository.deleteByDeviceIdAndPerformedDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} workout events directly", events.size());
        events.forEach(event -> {
            try {
                workoutProjector.projectWorkout(event);
            } catch (Exception e) {
                log.error("Failed to project workout event: {}", event.eventId().value(), e);
            }
        });
    }

    @Override
    public List<ExerciseDefinition> getAllExercises() {
        return exerciseCatalog.getAllExercises();
    }

    @Override
    public boolean exerciseExists(String exerciseId) {
        return exerciseDefinitionRepository.existsById(exerciseId);
    }

    @Override
    @Transactional
    public ExerciseDefinition createAutoExercise(String id, String name, String description,
                                                 String primaryMuscle, List<String> muscles) {
        ExerciseDefinitionEntity entity = ExerciseDefinitionEntity.createAutoCreated(
                id, name, description, primaryMuscle, muscles
        );
        ExerciseDefinitionEntity saved = exerciseDefinitionRepository.save(entity);
        log.info("Created auto-generated exercise: id={}, name={}", id, name);
        return new ExerciseDefinition(
                saved.getId(), saved.getName(), saved.getDescription(),
                saved.getPrimaryMuscle(), saved.getMuscles()
        );
    }

    @Override
    public PersonalRecordsResponse getAllPersonalRecords(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be blank");
        }
        return exerciseStatisticsService.getAllPersonalRecords(deviceId);
    }

}
