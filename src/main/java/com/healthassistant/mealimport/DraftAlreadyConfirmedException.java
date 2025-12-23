package com.healthassistant.mealimport;

import java.io.Serial;
import java.util.UUID;

class DraftAlreadyConfirmedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    DraftAlreadyConfirmedException(UUID draftId) {
        super("Draft already confirmed: " + draftId);
    }
}
