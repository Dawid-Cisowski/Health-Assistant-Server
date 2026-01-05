package com.healthassistant.workout.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutFacade {

    Optional<WorkoutDetailResponse> getWorkoutDetails(String workoutId);

    List<WorkoutDetailResponse> getWorkoutsByDateRange(String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteAllProjections();

    void deleteProjectionsByDeviceId(String deviceId);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);

    List<ExerciseDefinition> getAllExercises();

    boolean exerciseExists(String exerciseId);

    ExerciseDefinition createAutoExercise(String id, String name, String description,
                                          String primaryMuscle, List<String> muscles);

}
