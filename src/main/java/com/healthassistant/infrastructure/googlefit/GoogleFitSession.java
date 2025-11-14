package com.healthassistant.infrastructure.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;

@Getter
public class GoogleFitSession {
    @JsonProperty("id")
    private String id;

    @JsonProperty("activityType")
    private Integer activityType;

    @JsonProperty("startTimeMillis")
    private Long startTimeMillis;

    @JsonProperty("endTimeMillis")
    private Long endTimeMillis;

    @JsonProperty("packageName")
    private String packageName;

    public Instant getStartTime() {
        return startTimeMillis != null ? Instant.ofEpochMilli(startTimeMillis) : null;
    }

    public Instant getEndTime() {
        return endTimeMillis != null ? Instant.ofEpochMilli(endTimeMillis) : null;
    }

    public boolean isSleepSession() {
        return activityType != null && activityType == 72;
    }
}

