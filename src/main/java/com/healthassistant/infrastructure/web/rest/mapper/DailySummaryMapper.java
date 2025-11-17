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
                .avgHr(exercise.avgHr())
                .energyKcal(exercise.energyKcal())
                .build();
    }

    private static DailySummaryResponse.Sleep toSleepResponse(DailySummary.Sleep sleep) {
        return DailySummaryResponse.Sleep.builder()
                .start(sleep.start())
                .end(sleep.end())
                .totalMinutes(sleep.totalMinutes())
                .build();
    }

    private static DailySummaryResponse.Heart toHeartResponse(DailySummary.Heart heart) {
        return DailySummaryResponse.Heart.builder()
                .restingBpm(heart.restingBpm())
                .avgBpm(heart.avgBpm())
                .maxBpm(heart.maxBpm())
                .build();
    }
}

