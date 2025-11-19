package com.healthassistant.application.workout.query;

import com.healthassistant.application.workout.projection.WorkoutExerciseProjectionJpaEntity;
import com.healthassistant.application.workout.projection.WorkoutExerciseProjectionJpaRepository;
import com.healthassistant.application.workout.projection.WorkoutProjectionJpaEntity;
import com.healthassistant.application.workout.projection.WorkoutProjectionJpaRepository;
import com.healthassistant.application.workout.projection.WorkoutSetProjectionJpaEntity;
import com.healthassistant.application.workout.projection.WorkoutSetProjectionJpaRepository;
import com.healthassistant.dto.WorkoutDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WorkoutQueryService {

    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutExerciseProjectionJpaRepository exerciseRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;

    public Optional<WorkoutDetailResponse> getWorkoutDetails(String workoutId) {
        Optional<WorkoutProjectionJpaEntity> workoutOpt = workoutRepository.findByWorkoutId(workoutId);

        if (workoutOpt.isEmpty()) {
            return Optional.empty();
        }

        WorkoutProjectionJpaEntity workout = workoutOpt.get();

        // Fetch exercises for this workout
        List<WorkoutExerciseProjectionJpaEntity> exercises = workout.getExercises();

        // Fetch all sets for this workout
        List<WorkoutSetProjectionJpaEntity> sets = setRepository.findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(workoutId);

        // Group sets by exercise name
        var setsByExercise = sets.stream()
                .collect(Collectors.groupingBy(WorkoutSetProjectionJpaEntity::getExerciseName));

        // Map exercises to DTOs with their sets
        List<WorkoutDetailResponse.ExerciseDetail> exerciseDetails = exercises.stream()
                .sorted(Comparator.comparing(WorkoutExerciseProjectionJpaEntity::getOrderInWorkout))
                .map(exercise -> {
                    List<WorkoutSetProjectionJpaEntity> exerciseSets = setsByExercise.getOrDefault(
                            exercise.getExerciseName(),
                            List.of()
                    );

                    List<WorkoutDetailResponse.SetDetail> setDetails = exerciseSets.stream()
                            .sorted(Comparator.comparing(WorkoutSetProjectionJpaEntity::getSetNumber))
                            .map(set -> WorkoutDetailResponse.SetDetail.builder()
                                    .setNumber(set.getSetNumber())
                                    .weightKg(set.getWeightKg())
                                    .reps(set.getReps())
                                    .isWarmup(set.getIsWarmup())
                                    .volumeKg(set.getVolumeKg())
                                    .build())
                            .toList();

                    return WorkoutDetailResponse.ExerciseDetail.builder()
                            .exerciseName(exercise.getExerciseName())
                            .muscleGroup(exercise.getMuscleGroup())
                            .orderInWorkout(exercise.getOrderInWorkout())
                            .totalSets(exercise.getTotalSets())
                            .totalVolumeKg(exercise.getTotalVolumeKg())
                            .maxWeightKg(exercise.getMaxWeightKg())
                            .sets(setDetails)
                            .build();
                })
                .toList();

        WorkoutDetailResponse response = WorkoutDetailResponse.builder()
                .workoutId(workout.getWorkoutId())
                .performedAt(workout.getPerformedAt())
                .performedDate(workout.getPerformedDate())
                .source(workout.getSource())
                .note(workout.getNote())
                .totalExercises(workout.getTotalExercises())
                .totalSets(workout.getTotalSets())
                .totalVolumeKg(workout.getTotalVolumeKg())
                .totalWorkingVolumeKg(workout.getTotalWorkingVolumeKg())
                .exercises(exerciseDetails)
                .build();

        return Optional.of(response);
    }

    public List<WorkoutDetailResponse> getWorkoutsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<WorkoutProjectionJpaEntity> workouts = workoutRepository.findByPerformedDateBetweenOrderByPerformedAtDesc(
                startDate,
                endDate
        );

        return workouts.stream()
                .map(workout -> {
                    // For list view, we can skip fetching sets for performance
                    List<WorkoutDetailResponse.ExerciseDetail> exerciseDetails = workout.getExercises().stream()
                            .sorted(Comparator.comparing(WorkoutExerciseProjectionJpaEntity::getOrderInWorkout))
                            .map(exercise -> WorkoutDetailResponse.ExerciseDetail.builder()
                                    .exerciseName(exercise.getExerciseName())
                                    .muscleGroup(exercise.getMuscleGroup())
                                    .orderInWorkout(exercise.getOrderInWorkout())
                                    .totalSets(exercise.getTotalSets())
                                    .totalVolumeKg(exercise.getTotalVolumeKg())
                                    .maxWeightKg(exercise.getMaxWeightKg())
                                    .sets(List.of()) // Empty for list view
                                    .build())
                            .toList();

                    return WorkoutDetailResponse.builder()
                            .workoutId(workout.getWorkoutId())
                            .performedAt(workout.getPerformedAt())
                            .performedDate(workout.getPerformedDate())
                            .source(workout.getSource())
                            .note(workout.getNote())
                            .totalExercises(workout.getTotalExercises())
                            .totalSets(workout.getTotalSets())
                            .totalVolumeKg(workout.getTotalVolumeKg())
                            .totalWorkingVolumeKg(workout.getTotalWorkingVolumeKg())
                            .exercises(exerciseDetails)
                            .build();
                })
                .toList();
    }
}
