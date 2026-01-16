package com.healthassistant.workout;

import java.io.Serial;

final class WorkoutNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("PMD.UnusedFormalParameter")
    WorkoutNotFoundException(String eventId) {
        // Don't include eventId in message to prevent information disclosure
        super("Workout not found");
    }
}
