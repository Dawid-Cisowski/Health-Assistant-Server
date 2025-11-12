package com.healthassistant.dto.payload;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StepsPayload.class),
    @JsonSubTypes.Type(value = HeartRatePayload.class),
    @JsonSubTypes.Type(value = SleepSessionPayload.class),
    @JsonSubTypes.Type(value = ActiveCaloriesPayload.class),
    @JsonSubTypes.Type(value = ActiveMinutesPayload.class)
})
@Schema(
    description = "Event payload - structure depends on event type. The payload type is determined by the 'type' field in the parent EventEnvelope.",
    oneOf = {
        StepsPayload.class,
        HeartRatePayload.class,
        SleepSessionPayload.class,
        ActiveCaloriesPayload.class,
        ActiveMinutesPayload.class
    }
)
public sealed interface EventPayload permits
    StepsPayload,
    HeartRatePayload,
    SleepSessionPayload,
    ActiveCaloriesPayload,
    ActiveMinutesPayload {
}

