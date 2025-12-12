package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GoogleFitSession(
        @JsonProperty("id")
        String id,

        @JsonProperty("name")
        String name,

        @JsonProperty("activityType")
        Integer activityType,

        @JsonProperty("startTimeMillis")
        Long startTimeMillis,

        @JsonProperty("endTimeMillis")
        Long endTimeMillis,

        @JsonProperty("packageName")
        String packageName,

        @JsonProperty("application")
        Application application
) {
    public Instant getStartTime() {
        return startTimeMillis != null ? Instant.ofEpochMilli(startTimeMillis) : null;
    }

    public Instant getEndTime() {
        return endTimeMillis != null ? Instant.ofEpochMilli(endTimeMillis) : null;
    }

    public String getPackageName() {
        if (application != null && application.packageName() != null) {
            return application.packageName();
        }
        return packageName;
    }

    public boolean isSleepSession() {
        return activityType != null && activityType == 72;
    }

    public boolean isWalkingSession() {
        return activityType != null && activityType == 108;
    }

    public record Application(
            @JsonProperty("packageName")
            String packageName
    ) {
    }
}

