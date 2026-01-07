package com.healthassistant.meals;

import java.io.Serial;

final class BackdatingValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    BackdatingValidationException(String message) {
        super(message);
    }
}
