package com.healthassistant.healthevents.api.model;

import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;

import java.util.Objects;

public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "Idempotency key cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be blank");
        }
        if (value.length() > 512) {
            throw new IllegalArgumentException("Idempotency key cannot exceed 512 characters");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    public static IdempotencyKey from(
            String providedKey,
            String deviceId,
            String eventType,
            EventPayload payload,
            int index) {

        String keyValue;
        if (providedKey != null && !providedKey.isBlank()) {
            keyValue = providedKey;
        } else {
            String workoutId = payload instanceof WorkoutPayload w ? w.workoutId() : null;
            String sleepId = payload instanceof SleepSessionPayload s ? s.sleepId() : null;

            keyValue = generate(deviceId, eventType, workoutId, sleepId, index);
        }

        return new IdempotencyKey(keyValue);
    }

    private static String generate(
            String deviceId,
            String eventType,
            String workoutId,
            String sleepId,
            int index) {

        if ("WorkoutRecorded.v1".equals(eventType) && workoutId != null) {
            return deviceId + "|workout|" + workoutId;
        }

        if ("SleepSessionRecorded.v1".equals(eventType) && sleepId != null) {
            return deviceId + "|sleep|" + sleepId;
        }

        return deviceId + "|" + eventType + "|" + System.currentTimeMillis() + "-" + index;
    }
}
