package com.healthassistant.workout;

import java.io.Serial;

final class WorkoutBackdatingValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    WorkoutBackdatingValidationException(String message) {
        super(message);
    }
}
