package com.healthassistant.infrastructure.web.rest.mapper;

import com.healthassistant.domain.summary.DailySummary;
import com.healthassistant.dto.DailySummaryResponse;

import java.util.List;

public class DailySummaryMapper {

    public static DailySummaryResponse toResponse(DailySummary summary) {
        return DailySummaryResponse.builder()
                .date(summary.date())
                .activity(toActivityResponse(summary.activity()))
                .exercises(toExercisesResponse(summary.exercises()))
                .workouts(toWorkoutsResponse(summary.workouts()))
                .sleep(toSleepResponse(summary.sleep()))
                .heart(toHeartResponse(summary.heart()))
                .build();
    }

    private static DailySummaryResponse.Activity toActivityResponse(DailySummary.Activity activity) {
        return DailySummaryResponse.Activity.builder()
                .steps(activity.steps())
                .activeMinutes(activity.activeMinutes())
                .activeCalories(activity.activeCalories())
                .distanceMeters(activity.distanceMeters())
                .build();
    }

    private static List<DailySummaryResponse.Exercise> toExercisesResponse(List<DailySummary.Exercise> exercises) {
        return exercises.stream()
                .map(DailySummaryMapper::toExerciseResponse)
                .toList();
    }

    private static DailySummaryResponse.Exercise toExerciseResponse(DailySummary.Exercise exercise) {
        return DailySummaryResponse.Exercise.builder()
                .type(exercise.type())
                .start(exercise.start())
                .end(exercise.end())
                .durationMinutes(exercise.durationMinutes())
                .distanceMeters(exercise.distanceMeters())
                .steps(exercise.steps())
                .avgHr(exercise.avgHr())
                .energyKcal(exercise.energyKcal())
                .build();
    }

    private static List<DailySummaryResponse.Workout> toWorkoutsResponse(List<DailySummary.Workout> workouts) {
        return workouts.stream()
                .map(DailySummaryMapper::toWorkoutResponse)
                .toList();
    }

    private static DailySummaryResponse.Workout toWorkoutResponse(DailySummary.Workout workout) {
        return DailySummaryResponse.Workout.builder()
                .workoutId(workout.workoutId())
                .note(workout.note())
                .build();
    }

    private static List<DailySummaryResponse.Sleep> toSleepResponse(List<DailySummary.Sleep> sleepList) {
        return sleepList.stream()
                .map(sleep -> DailySummaryResponse.Sleep.builder()
                        .start(sleep.start())
                        .end(sleep.end())
                        .totalMinutes(sleep.totalMinutes())
                        .build())
                .toList();
    }

    private static DailySummaryResponse.Heart toHeartResponse(DailySummary.Heart heart) {
        return DailySummaryResponse.Heart.builder()
                .restingBpm(heart.restingBpm())
                .avgBpm(heart.avgBpm())
                .maxBpm(heart.maxBpm())
                .build();
    }
}

