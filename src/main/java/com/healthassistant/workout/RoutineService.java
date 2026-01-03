package com.healthassistant.workout;

import com.healthassistant.workout.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class RoutineService {

    private final RoutineRepository routineRepository;
    private final ExerciseCatalog exerciseCatalog;

    @Transactional(readOnly = true)
    List<RoutineListResponse> getRoutines(String deviceId) {
        return routineRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(this::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<RoutineResponse> getRoutine(UUID id, String deviceId) {
        return routineRepository.findByIdAndDeviceIdWithExercises(id, deviceId)
                .map(this::toResponse);
    }

    @Transactional
    RoutineResponse createRoutine(RoutineRequest request, String deviceId) {
        validateExerciseIds(request.exercises());

        RoutineEntity routine = new RoutineEntity();
        routine.setDeviceId(deviceId);
        routine.setName(request.name());
        routine.setDescription(request.description());
        routine.setColorTheme(request.colorTheme());

        for (RoutineExerciseRequest exerciseRequest : request.exercises()) {
            RoutineExerciseEntity exercise = new RoutineExerciseEntity();
            exercise.setExerciseId(exerciseRequest.exerciseId());
            exercise.setOrderIndex(exerciseRequest.orderIndex());
            exercise.setDefaultSets(exerciseRequest.defaultSets());
            exercise.setNotes(exerciseRequest.notes());
            routine.addExercise(exercise);
        }

        RoutineEntity saved = routineRepository.save(routine);
        log.info("Created routine '{}' with id {} for device {}", saved.getName(), saved.getId(), deviceId);
        return toResponse(saved);
    }

    @Transactional
    Optional<RoutineResponse> updateRoutine(UUID id, RoutineRequest request, String deviceId) {
        return routineRepository.findByIdAndDeviceIdWithExercises(id, deviceId)
                .map(routine -> {
                    validateExerciseIds(request.exercises());

                    routine.setName(request.name());
                    routine.setDescription(request.description());
                    routine.setColorTheme(request.colorTheme());

                    routine.clearExercises();
                    for (RoutineExerciseRequest exerciseRequest : request.exercises()) {
                        RoutineExerciseEntity exercise = new RoutineExerciseEntity();
                        exercise.setExerciseId(exerciseRequest.exerciseId());
                        exercise.setOrderIndex(exerciseRequest.orderIndex());
                        exercise.setDefaultSets(exerciseRequest.defaultSets());
                        exercise.setNotes(exerciseRequest.notes());
                        routine.addExercise(exercise);
                    }

                    RoutineEntity saved = routineRepository.save(routine);
                    log.info("Updated routine '{}' with id {} for device {}", saved.getName(), saved.getId(), deviceId);
                    return toResponse(saved);
                });
    }

    @Transactional
    boolean deleteRoutine(UUID id, String deviceId) {
        if (routineRepository.existsByIdAndDeviceId(id, deviceId)) {
            routineRepository.deleteByIdAndDeviceId(id, deviceId);
            log.info("Deleted routine with id {} for device {}", id, deviceId);
            return true;
        }
        return false;
    }

    private void validateExerciseIds(List<RoutineExerciseRequest> exercises) {
        List<ExerciseDefinition> allExercises = exerciseCatalog.getAllExercises();
        Set<String> validIds = allExercises.stream()
                .map(ExerciseDefinition::id)
                .collect(Collectors.toSet());

        List<String> invalidIds = exercises.stream()
                .map(RoutineExerciseRequest::exerciseId)
                .filter(id -> !validIds.contains(id))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid exercise IDs: " + invalidIds);
        }
    }

    private RoutineListResponse toListResponse(RoutineEntity entity) {
        return new RoutineListResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getColorTheme(),
                entity.getCreatedAt(),
                entity.getExercises().size()
        );
    }

    private RoutineResponse toResponse(RoutineEntity entity) {
        Map<String, String> exerciseNames = getExerciseNames(entity.getExercises());

        List<RoutineExerciseResponse> exerciseResponses = entity.getExercises().stream()
                .map(e -> new RoutineExerciseResponse(
                        e.getId(),
                        e.getExerciseId(),
                        exerciseNames.getOrDefault(e.getExerciseId(), e.getExerciseId()),
                        e.getOrderIndex(),
                        e.getDefaultSets(),
                        e.getNotes()
                ))
                .toList();

        return new RoutineResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getColorTheme(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                exerciseResponses
        );
    }

    private Map<String, String> getExerciseNames(List<RoutineExerciseEntity> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }

        return exerciseCatalog.getAllExercises().stream()
                .collect(Collectors.toMap(
                        ExerciseDefinition::id,
                        ExerciseDefinition::name,
                        (a, b) -> a
                ));
    }
}
