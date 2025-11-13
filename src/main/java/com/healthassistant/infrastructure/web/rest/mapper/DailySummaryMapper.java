package com.healthassistant.infrastructure.web.rest.mapper;

import com.healthassistant.domain.summary.DailySummary;
import com.healthassistant.dto.DailySummaryResponse;

import java.util.List;

public class DailySummaryMapper {

    public static DailySummaryResponse toResponse(DailySummary summary) {
        return DailySummaryResponse.builder()
                .date(summary.date())
                .activity(toActivityResponse(summary.activity()))
                .workouts(toWorkoutsResponse(summary.workouts()))
                .sleep(toSleepResponse(summary.sleep()))
                .heart(toHeartResponse(summary.heart()))
                .score(toScoreResponse(summary.score()))
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

    private static List<DailySummaryResponse.Workout> toWorkoutsResponse(List<DailySummary.Workout> workouts) {
        return workouts.stream()
                .map(DailySummaryMapper::toWorkoutResponse)
                .toList();
    }

    private static DailySummaryResponse.Workout toWorkoutResponse(DailySummary.Workout workout) {
        return DailySummaryResponse.Workout.builder()
                .type(workout.type())
                .start(workout.start())
                .end(workout.end())
                .durationMinutes(workout.durationMinutes())
                .distanceMeters(workout.distanceMeters())
                .avgHr(workout.avgHr())
                .energyKcal(workout.energyKcal())
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

    private static DailySummaryResponse.Score toScoreResponse(DailySummary.Score score) {
        return DailySummaryResponse.Score.builder()
                .activityScore(score.activityScore())
                .sleepScore(score.sleepScore())
                .readinessScore(score.readinessScore())
                .overallScore(score.overallScore())
                .build();
    }
}

