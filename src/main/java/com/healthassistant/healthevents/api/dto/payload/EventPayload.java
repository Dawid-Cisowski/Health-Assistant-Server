package com.healthassistant.healthevents.api.dto.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.model.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@JsonSubTypes({
    @JsonSubTypes.Type(value = StepsPayload.class),
    @JsonSubTypes.Type(value = DistanceBucketPayload.class),
    @JsonSubTypes.Type(value = HeartRatePayload.class),
    @JsonSubTypes.Type(value = RestingHeartRatePayload.class),
    @JsonSubTypes.Type(value = SleepSessionPayload.class),
    @JsonSubTypes.Type(value = ActiveCaloriesPayload.class),
    @JsonSubTypes.Type(value = ActiveMinutesPayload.class),
    @JsonSubTypes.Type(value = WalkingSessionPayload.class),
    @JsonSubTypes.Type(value = WorkoutPayload.class),
    @JsonSubTypes.Type(value = MealRecordedPayload.class),
    @JsonSubTypes.Type(value = WeightMeasurementPayload.class),
    @JsonSubTypes.Type(value = EventDeletedPayload.class),
    @JsonSubTypes.Type(value = EventCorrectedPayload.class)
})
@Schema(
    description = "Event payload - structure depends on event type. The payload type is determined by the 'type' field in the parent EventEnvelope.",
    oneOf = {
        StepsPayload.class,
        DistanceBucketPayload.class,
        HeartRatePayload.class,
        RestingHeartRatePayload.class,
        SleepSessionPayload.class,
        ActiveCaloriesPayload.class,
        ActiveMinutesPayload.class,
        WalkingSessionPayload.class,
        WorkoutPayload.class,
        MealRecordedPayload.class,
        WeightMeasurementPayload.class,
        EventDeletedPayload.class,
        EventCorrectedPayload.class
    }
)
public sealed interface EventPayload permits
    StepsPayload,
    DistanceBucketPayload,
    HeartRatePayload,
    RestingHeartRatePayload,
    SleepSessionPayload,
    ActiveCaloriesPayload,
    ActiveMinutesPayload,
    WalkingSessionPayload,
    WorkoutPayload,
    MealRecordedPayload,
    WeightMeasurementPayload,
    EventDeletedPayload,
    EventCorrectedPayload {

    static Class<? extends EventPayload> payloadClassFor(EventType eventType) {
        return payloadClassForType(eventType.value());
    }

    static Class<? extends EventPayload> payloadClassForType(String eventTypeValue) {
        return switch (eventTypeValue) {
            case "StepsBucketedRecorded.v1" -> StepsPayload.class;
            case "DistanceBucketRecorded.v1" -> DistanceBucketPayload.class;
            case "HeartRateSummaryRecorded.v1" -> HeartRatePayload.class;
            case "RestingHeartRateRecorded.v1" -> RestingHeartRatePayload.class;
            case "SleepSessionRecorded.v1" -> SleepSessionPayload.class;
            case "ActiveCaloriesBurnedRecorded.v1" -> ActiveCaloriesPayload.class;
            case "ActiveMinutesRecorded.v1" -> ActiveMinutesPayload.class;
            case "WalkingSessionRecorded.v1" -> WalkingSessionPayload.class;
            case "WorkoutRecorded.v1" -> WorkoutPayload.class;
            case "MealRecorded.v1" -> MealRecordedPayload.class;
            case "WeightMeasurementRecorded.v1" -> WeightMeasurementPayload.class;
            case "EventDeleted.v1" -> EventDeletedPayload.class;
            case "EventCorrected.v1" -> EventCorrectedPayload.class;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventTypeValue);
        };
    }

    static EventPayload fromMap(String eventTypeStr, Map<String, Object> payload, ObjectMapper mapper) {
        EventType eventType = EventType.from(eventTypeStr);
        Class<? extends EventPayload> clazz = payloadClassFor(eventType);
        return mapper.convertValue(payload, clazz);
    }
}

