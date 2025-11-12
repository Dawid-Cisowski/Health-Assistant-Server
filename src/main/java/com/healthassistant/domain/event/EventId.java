package com.healthassistant.domain.event;

import java.util.Objects;

public record EventId(String value) {

    public EventId {
        Objects.requireNonNull(value, "Event ID cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Event ID cannot be blank");
        }
        if (!value.startsWith("evt_")) {
            throw new IllegalArgumentException("Event ID must start with 'evt_'");
        }
        if (value.length() > 32) {
            throw new IllegalArgumentException("Event ID cannot exceed 32 characters");
        }
    }

    public static EventId of(String value) {
        return new EventId(value);
    }
}

