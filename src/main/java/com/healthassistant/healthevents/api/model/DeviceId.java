package com.healthassistant.healthevents.api.model;

import java.util.Objects;

public record DeviceId(String value) {

    public DeviceId {
        Objects.requireNonNull(value, "Device ID cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Device ID cannot be blank");
        }
        if (value.length() > 128) {
            throw new IllegalArgumentException("Device ID cannot exceed 128 characters");
        }
    }

    public static DeviceId of(String value) {
        return new DeviceId(value);
    }
}
