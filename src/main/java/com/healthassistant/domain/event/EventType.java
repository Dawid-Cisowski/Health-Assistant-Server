package com.healthassistant.domain.event;

import java.util.Objects;

public sealed interface EventType permits
        StepsBucketedRecorded,
        DistanceBucketRecorded,
        HeartRateSummaryRecorded,
        SleepSessionRecorded,
        ActiveCaloriesBurnedRecorded,
        ActiveMinutesRecorded,
        WalkingSessionRecorded,
        WorkoutRecorded {

    String value();

    static EventType from(String value) {
        Objects.requireNonNull(value, "Event type cannot be null");
        return switch (value) {
            case "StepsBucketedRecorded.v1" -> new StepsBucketedRecorded();
            case "DistanceBucketRecorded.v1" -> new DistanceBucketRecorded();
            case "HeartRateSummaryRecorded.v1" -> new HeartRateSummaryRecorded();
            case "SleepSessionRecorded.v1" -> new SleepSessionRecorded();
            case "ActiveCaloriesBurnedRecorded.v1" -> new ActiveCaloriesBurnedRecorded();
            case "ActiveMinutesRecorded.v1" -> new ActiveMinutesRecorded();
            case "WalkingSessionRecorded.v1" -> new WalkingSessionRecorded();
            case "WorkoutRecorded.v1" -> new WorkoutRecorded();
            default -> throw new IllegalArgumentException("Unknown event type: " + value);
        };
    }

}

record StepsBucketedRecorded() implements EventType {
    @Override
    public String value() {
        return "StepsBucketedRecorded.v1";
    }
}

record DistanceBucketRecorded() implements EventType {
    @Override
    public String value() {
        return "DistanceBucketRecorded.v1";
    }
}

record HeartRateSummaryRecorded() implements EventType {
    @Override
    public String value() {
        return "HeartRateSummaryRecorded.v1";
    }
}

record SleepSessionRecorded() implements EventType {
    @Override
    public String value() {
        return "SleepSessionRecorded.v1";
    }
}

record ActiveCaloriesBurnedRecorded() implements EventType {
    @Override
    public String value() {
        return "ActiveCaloriesBurnedRecorded.v1";
    }
}

record ActiveMinutesRecorded() implements EventType {
    @Override
    public String value() {
        return "ActiveMinutesRecorded.v1";
    }
}

record WalkingSessionRecorded() implements EventType {
    @Override
    public String value() {
        return "WalkingSessionRecorded.v1";
    }
}

record WorkoutRecorded() implements EventType {
    @Override
    public String value() {
        return "WorkoutRecorded.v1";
    }
}

