package com.healthassistant.healthevents;

record EventValidationError(String field, String message) {

    static EventValidationError missingField(String field) {
        return new EventValidationError(field, "Missing required field: " + field);
    }

    static EventValidationError invalidValue(String field, String reason) {
        return new EventValidationError(field, field + ": " + reason);
    }

    static EventValidationError invalidEventType(String type) {
        return new EventValidationError("type", "Invalid event type: " + type);
    }

    static EventValidationError emptyPayload() {
        return new EventValidationError("payload", "Payload cannot be empty");
    }
}
