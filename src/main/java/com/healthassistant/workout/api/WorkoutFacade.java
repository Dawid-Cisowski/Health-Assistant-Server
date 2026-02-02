package com.healthassistant.workout.api;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse;
import com.healthassistant.workout.api.dto.UpdateWorkoutRequest;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import com.healthassistant.workout.api.dto.WorkoutMutationResponse;
import com.healthassistant.workout.api.dto.WorkoutReprojectionResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutFacade {

    Optional<WorkoutDetailResponse> getWorkoutDetails(String deviceId, String workoutId);

    List<WorkoutDetailResponse> getWorkoutsByDateRange(String deviceId, LocalDate startDate, LocalDate endDate);

    boolean hasWorkoutOnDate(String deviceId, LocalDate date);

    void deleteProjectionsForDate(String deviceId, LocalDate date);

    void projectEvents(List<StoredEventData> events);

    List<ExerciseDefinition> getAllExercises();

    boolean exerciseExists(String exerciseId);

    ExerciseDefinition createAutoExercise(String id, String name, String description,
                                          String primaryMuscle, List<String> muscles);

    PersonalRecordsResponse getAllPersonalRecords(String deviceId);

    void deleteWorkout(String deviceId, String eventId);

    WorkoutMutationResponse updateWorkout(String deviceId, String eventId, UpdateWorkoutRequest request);

    WorkoutReprojectionResponse reprojectAllWorkouts(String deviceId);

}
