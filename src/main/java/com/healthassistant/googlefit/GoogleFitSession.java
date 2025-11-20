package com.healthassistant.googlefit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;

@Getter
public class GoogleFitSession {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("activityType")
    private Integer activityType;

    @JsonProperty("startTimeMillis")
    private Long startTimeMillis;

    @JsonProperty("endTimeMillis")
    private Long endTimeMillis;

    @JsonProperty("packageName")
    private String packageName;

    @JsonProperty("application")
    private Application application;

    public Instant getStartTime() {
        return startTimeMillis != null ? Instant.ofEpochMilli(startTimeMillis) : null;
    }

    public Instant getEndTime() {
        return endTimeMillis != null ? Instant.ofEpochMilli(endTimeMillis) : null;
    }

    public String getPackageName() {
        if (application != null && application.packageName != null) {
            return application.packageName;
        }
        return packageName;
    }

    public boolean isSleepSession() {
        return activityType != null && activityType == 72;
    }

    public boolean isWalkingSession() {
        return activityType != null && activityType == 108;
    }

    @Getter
    public static class Application {
        @JsonProperty("packageName")
        private String packageName;
    }
}

