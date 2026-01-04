package com.healthassistant.healthevents.api.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Objects;

@JsonDeserialize(using = EventTypeDeserializer.class)
public sealed interface EventType permits
        StepsBucketedRecorded,
        DistanceBucketRecorded,
        HeartRateSummaryRecorded,
        SleepSessionRecorded,
        ActiveCaloriesBurnedRecorded,
        ActiveMinutesRecorded,
        WalkingSessionRecorded,
        WorkoutRecorded,
        MealRecorded,
        EventDeleted,
        EventCorrected {

    @JsonValue
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
            case "MealRecorded.v1" -> new MealRecorded();
            case "EventDeleted.v1" -> new EventDeleted();
            case "EventCorrected.v1" -> new EventCorrected();
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

record MealRecorded() implements EventType {
    @Override
    public String value() {
        return "MealRecorded.v1";
    }
}

record EventDeleted() implements EventType {
    @Override
    public String value() {
        return "EventDeleted.v1";
    }
}

record EventCorrected() implements EventType {
    @Override
    public String value() {
        return "EventCorrected.v1";
    }
}
