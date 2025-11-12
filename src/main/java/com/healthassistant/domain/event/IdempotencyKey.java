package com.healthassistant.domain.event;

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
}

