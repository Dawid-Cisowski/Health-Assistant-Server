package com.healthassistant.domain.event;

public record EventValidationError(String field, String message) {

    public static EventValidationError missingField(String field) {
        return new EventValidationError(field, "Missing required field: " + field);
    }

    public static EventValidationError invalidValue(String field, String reason) {
        return new EventValidationError(field, field + ": " + reason);
    }

    public static EventValidationError invalidEventType(String type) {
        return new EventValidationError("type", "Invalid event type: " + type);
    }

    public static EventValidationError emptyPayload() {
        return new EventValidationError("payload", "Payload cannot be empty");
    }
}

