package com.healthassistant.healthevents.api.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
            Map<String, Object> payload,
            int index) {

        String keyValue;
        if (providedKey != null && !providedKey.isBlank()) {
            keyValue = providedKey;
        } else {
            String workoutId = Optional.ofNullable(payload.get("workoutId"))
                    .map(Object::toString)
                    .orElse(null);
            String sleepId = Optional.ofNullable(payload.get("sleepId"))
                    .map(Object::toString)
                    .orElse(null);

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
